# Import agents for backward compatibility
from agents import ResearchAgent, CodeAgent, DecisionAgent
"""
Main entry point for the AI Knowledge Orchestrator.
Provides FastAPI server with REST endpoints and WebSocket support.
"""

import asyncio
from datetime import datetime
from typing import Dict, Any, List, Optional
import uuid

from fastapi import FastAPI, HTTPException, Depends, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import pymongo
from pymongo import MongoClient
from motor.motor_asyncio import AsyncIOMotorClient
import uvicorn

from config import settings, logger
from models import (
    QueryRequest,
    QueryResponse,
    QueryStatus,
    QueryType,
    QueryDocument,
    ResponseDocument,
    ErrorResponse,
    HealthStatus,
    WebSocketMessage,
    WebSocketQueryUpdate,
    BatchQueryRequest,
    BatchQueryResponse,
)
from workflow import OrchestrationWorkflow, AgentCommunicationProtocol


# Global variables for workflow and database
mongodb_client = None
workflow = None
communication_protocol = None
active_queries = {}
websocket_connections = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Manage application lifecycle.
    Initialize resources on startup and cleanup on shutdown.
    """
    # Startup
    logger.info("Starting AI Knowledge Orchestrator...")
    
    # Initialize MongoDB connection
    global mongodb_client
    try:
        mongodb_client = AsyncIOMotorClient(settings.get_mongodb_url())
        await mongodb_client.server_info()  # Test connection
        logger.info("Connected to MongoDB successfully")
    except Exception as e:
        logger.error(f"Failed to connect to MongoDB: {e}")
        # Continue without MongoDB (limited functionality)
    
    # Initialize workflow and communication protocol
    global workflow, communication_protocol
    try:
        # Initialize agents
        research_agent = ResearchAgent(mongodb_client=mongodb_client)
        code_agent = CodeAgent(mongodb_client=mongodb_client)
        decision_agent = DecisionAgent(
            research_agent=research_agent,
            code_agent=code_agent
        )
        
        # Initialize workflow
        workflow = OrchestrationWorkflow()
        
        # Initialize communication protocol
        communication_protocol = AgentCommunicationProtocol()
        communication_protocol.register_agent("research", research_agent)
        communication_protocol.register_agent("code", code_agent)
        communication_protocol.register_agent("decision", decision_agent)
        
        logger.info("Workflow and agents initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize workflow: {e}")
        raise
    
    yield
    
    # Shutdown
    logger.info("Shutting down AI Knowledge Orchestrator...")
    
    # Close MongoDB connection
    if mongodb_client:
        mongodb_client.close()
    
    # Cleanup workflow and protocol
    workflow = None
    if communication_protocol:
        communication_protocol.agent_registry.clear()
    
    logger.info("Shutdown complete")


# Create FastAPI app
app = FastAPI(
    title="AI Knowledge Orchestrator",
    description="Multi-agent AI orchestration system with LangChain",
    version=settings.app_version,
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Dependency to get database
async def get_database():
    """Get MongoDB database instance."""
    if mongodb_client:
        return mongodb_client[settings.mongodb_database]
    return None


# Health check endpoint
@app.get("/health", response_model=HealthStatus)
async def health_check():
    """
    Health check endpoint to verify service status.
    """
    # Check MongoDB connection
    mongodb_status = False
    if mongodb_client:
        try:
            await mongodb_client.server_info()
            mongodb_status = True
        except:
            mongodb_status = False
    
    # Check backend API (Spring Boot)
    backend_status = False
    try:
        import httpx
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{settings.backend_api_url}/actuator/health",
                timeout=5.0
            )
            backend_status = response.status_code == 200
    except:
        backend_status = False
    
    # Check LLM provider
    llm_status = False
    try:
        if settings.llm_provider == "openai" and settings.openai_api_key:
            llm_status = True
        elif settings.llm_provider == "ollama":
            # Check Ollama connection
            import httpx
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    f"{settings.ollama_base_url}/api/tags",
                    timeout=5.0
                )
                llm_status = response.status_code == 200
    except:
        llm_status = False
    
    # Check agent status via communication protocol
    agent_statuses = {}
    if communication_protocol:
        agent_statuses = communication_protocol.get_all_agents_status()
    
    return HealthStatus(
        status="healthy" if all([mongodb_status, llm_status]) else "degraded",
        version=settings.app_version,
        timestamp=datetime.utcnow(),
        mongodb=mongodb_status,
        backend_api=backend_status,
        llm_provider=llm_status,
        agents=agent_statuses,
    )


# Query processing endpoint
@app.post("/api/v1/query", response_model=QueryResponse)
async def process_query(
    request: QueryRequest,
    db=Depends(get_database)
):
    """
    Process a query through the multi-agent system.
    """
    query_id = str(uuid.uuid4())
    
    try:
        logger.info(f"Processing query {query_id}: {request.query_text[:100]}...")
        
        # Store query in MongoDB if available
        if db:
            query_doc = QueryDocument(
                user_id=request.user_id or "anonymous",
                query_text=request.query_text,
                query_type=request.query_type,
                status=QueryStatus.PENDING,
                priority=request.priority,
                context=request.context,
                metadata=request.metadata,
            )
            await db.queries.insert_one(query_doc.dict(by_alias=True))
        
        # Update status to processing
        active_queries[query_id] = QueryStatus.PROCESSING
        
        # Process through workflow
        if not workflow:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Workflow not initialized"
            )
        
        # Execute workflow
        result = await workflow.execute(
            query_id=query_id,
            query=request.query_text,
            query_type=request.query_type,
            metadata=request.metadata
        )
        
        # Extract response from workflow result
        response_content = result.get("response", "No response generated")
        confidence = result.get("confidence", 0.0)
        processing_times = result.get("processing_times", {})
        agents_used = result.get("agents_used", [])
        
        # Update query status in MongoDB
        if db:
            await db.queries.update_one(
                {"_id": query_doc.id},
                {
                    "$set": {
                        "status": QueryStatus.COMPLETED,
                        "updated_at": datetime.utcnow(),
                    }
                }
            )
            
            # Store response
            from models import ResponseDocument, AgentType
            response_doc = ResponseDocument(
                query_id=query_id,
                agent_type=AgentType.ORCHESTRATOR,
                content=response_content,
                confidence=confidence,
                processing_time=sum(processing_times.values()),
                sources=[],
                metadata={
                    "agents_used": agents_used,
                    "processing_times": processing_times
                },
            )
            await db.responses.insert_one(response_doc.dict(by_alias=True))
        
        # Create query response
        from models import AgentResponse
        query_response = QueryResponse(
            id=query_id,
            query_text=request.query_text,
            query_type=request.query_type,
            status=result.get("status", QueryStatus.COMPLETED),
            response=response_content,
            agent_responses=[],  # Workflow handles internally
            confidence=confidence,
            processing_time=sum(processing_times.values()),
            completed_at=datetime.utcnow(),
            metadata={
                "agents_used": agents_used,
                "processing_times": processing_times,
                "errors": result.get("errors", [])
            },
        )
        
        # Update active queries
        active_queries[query_id] = QueryStatus.COMPLETED
        
        # Send WebSocket update if connected
        await send_websocket_update(query_id, QueryStatus.COMPLETED, 1.0, "Query completed")
        
        return query_response
        
    except Exception as e:
        logger.error(f"Error processing query {query_id}: {e}")
        
        # Update status
        active_queries[query_id] = QueryStatus.FAILED
        
        # Update in MongoDB
        if db:
            await db.queries.update_one(
                {"_id": query_id},
                {
                    "$set": {
                        "status": QueryStatus.FAILED,
                        "updated_at": datetime.utcnow(),
                    }
                }
            )
        
        # Send WebSocket update
        await send_websocket_update(query_id, QueryStatus.FAILED, 0.0, str(e))
        
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# Batch query processing
@app.post("/api/v1/batch", response_model=BatchQueryResponse)
async def process_batch(
    request: BatchQueryRequest,
    db=Depends(get_database)
):
    """
    Process multiple queries in batch.
    """
    batch_id = request.batch_id or str(uuid.uuid4())
    
    try:
        logger.info(f"Processing batch {batch_id} with {len(request.queries)} queries")
        
        results = []
        completed = 0
        failed = 0
        
        for query_request in request.queries:
            try:
                # Process each query
                response = await process_query(query_request, db)
                results.append(response)
                completed += 1
            except Exception as e:
                logger.error(f"Failed to process query in batch: {e}")
                failed += 1
                # Add failed response
                results.append(QueryResponse(
                    id=str(uuid.uuid4()),
                    query_text=query_request.query_text,
                    query_type=query_request.query_type,
                    status=QueryStatus.FAILED,
                    response=str(e),
                    confidence=0.0,
                    processing_time=0.0,
                ))
        
        return BatchQueryResponse(
            batch_id=batch_id,
            total_queries=len(request.queries),
            completed=completed,
            failed=failed,
            results=results,
            status="COMPLETED" if failed == 0 else "PARTIAL",
            completed_at=datetime.utcnow(),
        )
        
    except Exception as e:
        logger.error(f"Error processing batch {batch_id}: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# Get query status
@app.get("/api/v1/query/{query_id}")
async def get_query_status(query_id: str, db=Depends(get_database)):
    """
    Get the status of a specific query.
    """
    try:
        # Check active queries first
        if query_id in active_queries:
            return {
                "query_id": query_id,
                "status": active_queries[query_id],
            }
        
        # Check database
        if db:
            query = await db.queries.find_one({"_id": query_id})
            if query:
                return {
                    "query_id": query_id,
                    "status": query.get("status"),
                    "created_at": query.get("created_at"),
                    "updated_at": query.get("updated_at"),
                }
        
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Query {query_id} not found"
        )
        
    except Exception as e:
        logger.error(f"Error getting query status: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# WebSocket endpoint for real-time updates
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """
    WebSocket endpoint for real-time query updates.
    """
    await websocket.accept()
    connection_id = str(uuid.uuid4())
    websocket_connections[connection_id] = websocket
    
    try:
        logger.info(f"WebSocket connection established: {connection_id}")
        
        # Send welcome message
        await websocket.send_json({
            "type": "connection",
            "message": "Connected to AI Knowledge Orchestrator",
            "connection_id": connection_id,
        })
        
        # Keep connection alive
        while True:
            # Receive messages
            data = await websocket.receive_json()
            
            # Handle different message types
            if data.get("type") == "ping":
                await websocket.send_json({"type": "pong"})
            
            elif data.get("type") == "subscribe":
                query_id = data.get("query_id")
                logger.info(f"Subscribing to updates for query: {query_id}")
                # Store subscription (simplified - in production, use proper subscription management)
                
            elif data.get("type") == "query":
                # Process query through WebSocket
                request = QueryRequest(**data.get("data", {}))
                response = await process_query(request, None)
                await websocket.send_json({
                    "type": "query_response",
                    "data": response.dict(),
                })
            
    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected: {connection_id}")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
    finally:
        # Remove connection
        if connection_id in websocket_connections:
            del websocket_connections[connection_id]


async def send_websocket_update(
    query_id: str,
    status: QueryStatus,
    progress: float,
    message: str
):
    """
    Send update to all connected WebSocket clients.
    """
    update = WebSocketQueryUpdate(
        query_id=query_id,
        status=status,
        progress=progress,
        message=message,
    )
    
    # Send to all connected clients
    for connection_id, websocket in list(websocket_connections.items()):
        try:
            await websocket.send_json({
                "type": "query_update",
                "data": update.dict(),
            })
        except Exception as e:
            logger.error(f"Failed to send WebSocket update: {e}")
            # Remove failed connection
            del websocket_connections[connection_id]


# Agent-specific endpoints
@app.get("/api/v1/agents")
async def get_agents_status():
    """
    Get status of all available agents.
    """
    if communication_protocol:
        return communication_protocol.get_all_agents_status()
    return {"error": "Communication protocol not initialized"}


@app.post("/api/v1/agents/research/add-knowledge")
async def add_to_knowledge_base(documents: List[Dict[str, Any]]):
    """
    Add documents to the knowledge base for RAG.
    """
    if communication_protocol:
        research_agent = communication_protocol.agent_registry.get("research")
        if not research_agent:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Research agent not available"
            )
    else:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Communication protocol not initialized"
        )
    
    try:
        success = await research_agent.add_to_knowledge_base(documents)
        return {
            "success": success,
            "documents_added": len(documents),
        }
    except Exception as e:
        logger.error(f"Error adding to knowledge base: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )


# Error handler
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """
    Global exception handler.
    """
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=ErrorResponse(
            error="Internal Server Error",
            message=str(exc),
            details={"path": str(request.url)},
        ).dict(),
    )


# Main entry point
if __name__ == "__main__":
    # Run the server
    uvicorn.run(
        "main:app",
        host=settings.api_host,
        port=settings.api_port,
        reload=settings.api_reload,
        workers=settings.api_workers,
        log_level=settings.log_level.lower(),
    )
