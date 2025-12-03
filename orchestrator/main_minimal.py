"""
Minimal AI Knowledge Orchestrator - FastAPI server without LangChain.
This version works without the complex dependencies.
"""

from datetime import datetime
from typing import Dict, Any, List, Optional
import uuid

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import uvicorn

# Simple models
class QueryRequest(BaseModel):
    query_text: str
    query_type: str = "GENERAL"
    metadata: Optional[Dict[str, Any]] = None

class QueryResponse(BaseModel):
    id: str
    query_text: str
    response: str
    status: str
    confidence: float = 0.8
    processing_time: float = 0.0

class HealthStatus(BaseModel):
    status: str
    version: str = "1.0.0"
    timestamp: datetime

# Create FastAPI app
app = FastAPI(
    title="AI Knowledge Orchestrator (Minimal)",
    description="Simplified version without LangChain dependencies",
    version="1.0.0",
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Health check endpoint
@app.get("/health", response_model=HealthStatus)
async def health_check():
    """Health check endpoint."""
    return HealthStatus(
        status="healthy",
        version="1.0.0",
        timestamp=datetime.utcnow()
    )

# Query processing endpoint
@app.post("/api/v1/query", response_model=QueryResponse)
async def process_query(request: QueryRequest):
    """Process a query with mock response."""
    query_id = str(uuid.uuid4())
    
    # Mock response for testing
    mock_responses = {
        "RESEARCH": f"Based on my research, here's what I found about '{request.query_text}': This is a mock research response.",
        "CODE": f"Here's a code solution for '{request.query_text}':\n```python\n# Mock code response\nprint('Hello World')\n```",
        "GENERAL": f"Regarding your query '{request.query_text}': This is a mock general response."
    }
    
    response_text = mock_responses.get(
        request.query_type, 
        f"Processing query: {request.query_text}"
    )
    
    return QueryResponse(
        id=query_id,
        query_text=request.query_text,
        response=response_text,
        status="COMPLETED",
        confidence=0.8,
        processing_time=0.5
    )

# Get all agents status
@app.get("/api/v1/agents")
async def get_agents_status():
    """Get status of agents."""
    return {
        "research": {"status": "active", "type": "mock"},
        "code": {"status": "active", "type": "mock"},
        "decision": {"status": "active", "type": "mock"}
    }

# Error handler
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler."""
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": "Internal Server Error",
            "message": str(exc),
            "path": str(request.url)
        }
    )

if __name__ == "__main__":
    print("Starting Minimal AI Knowledge Orchestrator...")
    print("This version runs without LangChain dependencies")
    print("API will be available at http://localhost:8000")
    print("API docs at http://localhost:8000/docs")
    
    uvicorn.run(
        "main_minimal:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info"
    )
