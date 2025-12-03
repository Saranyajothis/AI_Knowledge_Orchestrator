"""
Data models and schemas for the orchestrator.
Defines Pydantic models for request/response validation.
"""

from datetime import datetime
from enum import Enum
from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field, validator
from bson import ObjectId


class PyObjectId(ObjectId):
    """Custom ObjectId type for Pydantic models."""
    
    @classmethod
    def __get_validators__(cls):
        yield cls.validate
    
    @classmethod
    def validate(cls, v):
        if not ObjectId.is_valid(v):
            raise ValueError("Invalid ObjectId")
        return ObjectId(v)
    
    @classmethod
    def __modify_schema__(cls, field_schema):
        field_schema.update(type="string")


class QueryType(str, Enum):
    """Types of queries that can be processed."""
    RESEARCH = "RESEARCH"
    CODE = "CODE"
    DECISION = "DECISION"
    GENERAL = "GENERAL"


class QueryStatus(str, Enum):
    """Status of query processing."""
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class AgentType(str, Enum):
    """Types of agents in the system."""
    RESEARCH = "RESEARCH"
    CODE = "CODE"
    DECISION = "DECISION"
    ORCHESTRATOR = "ORCHESTRATOR"


class ProcessingPriority(str, Enum):
    """Priority levels for query processing."""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


# Request Models
class QueryRequest(BaseModel):
    """Request model for submitting a query."""
    query_text: str = Field(..., min_length=1, max_length=10000, description="The query text")
    query_type: Optional[QueryType] = Field(QueryType.GENERAL, description="Type of query")
    context: Optional[Dict[str, Any]] = Field(default={}, description="Additional context")
    metadata: Optional[Dict[str, Any]] = Field(default={}, description="Query metadata")
    priority: Optional[ProcessingPriority] = Field(
        ProcessingPriority.MEDIUM,
        description="Processing priority"
    )
    user_id: Optional[str] = Field(None, description="User ID from Spring Boot backend")
    session_id: Optional[str] = Field(None, description="Session ID for conversation context")
    
    @validator("query_text")
    def validate_query_text(cls, v):
        """Validate query text is not empty."""
        if not v or v.isspace():
            raise ValueError("Query text cannot be empty")
        return v.strip()


class AgentContext(BaseModel):
    """Context information passed between agents."""
    query_id: str = Field(..., description="Unique query ID")
    original_query: str = Field(..., description="Original query text")
    query_type: QueryType = Field(..., description="Type of query")
    processed_data: Dict[str, Any] = Field(default={}, description="Data processed by agents")
    agent_chain: List[AgentType] = Field(default=[], description="Chain of agents that processed")
    metadata: Dict[str, Any] = Field(default={}, description="Additional metadata")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


# Response Models
class AgentResponse(BaseModel):
    """Response from an individual agent."""
    agent_type: AgentType = Field(..., description="Type of agent")
    content: str = Field(..., description="Response content")
    confidence: float = Field(0.0, ge=0.0, le=1.0, description="Confidence score")
    processing_time: float = Field(..., description="Processing time in seconds")
    metadata: Optional[Dict[str, Any]] = Field(default={}, description="Agent metadata")
    sources: Optional[List[str]] = Field(default=[], description="Sources used")
    error: Optional[str] = Field(None, description="Error message if failed")


class QueryResponse(BaseModel):
    """Complete response for a query."""
    id: str = Field(..., description="Query ID")
    query_text: str = Field(..., description="Original query text")
    query_type: QueryType = Field(..., description="Type of query")
    status: QueryStatus = Field(..., description="Current status")
    response: Optional[str] = Field(None, description="Final response")
    agent_responses: List[AgentResponse] = Field(default=[], description="Individual agent responses")
    confidence: float = Field(0.0, ge=0.0, le=1.0, description="Overall confidence")
    processing_time: float = Field(0.0, description="Total processing time")
    created_at: datetime = Field(default_factory=datetime.utcnow)
    completed_at: Optional[datetime] = Field(None)
    metadata: Dict[str, Any] = Field(default={})


# MongoDB Models
class QueryDocument(BaseModel):
    """MongoDB document model for queries."""
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    user_id: str = Field(..., description="User ID")
    query_text: str = Field(..., description="Query text")
    query_type: QueryType = Field(..., description="Query type")
    status: QueryStatus = Field(QueryStatus.PENDING, description="Status")
    priority: ProcessingPriority = Field(ProcessingPriority.MEDIUM)
    context: Dict[str, Any] = Field(default={})
    metadata: Dict[str, Any] = Field(default={})
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        json_encoders = {ObjectId: str}
        schema_extra = {
            "example": {
                "user_id": "507f1f77bcf86cd799439011",
                "query_text": "What are the best practices for MongoDB indexing?",
                "query_type": "RESEARCH",
                "status": "PENDING",
                "priority": "HIGH",
            }
        }


class ResponseDocument(BaseModel):
    """MongoDB document model for responses."""
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    query_id: str = Field(..., description="Reference to query")
    agent_type: AgentType = Field(..., description="Agent that generated response")
    content: str = Field(..., description="Response content")
    confidence: float = Field(..., ge=0.0, le=1.0)
    processing_time: float = Field(...)
    sources: List[str] = Field(default=[])
    metadata: Dict[str, Any] = Field(default={})
    created_at: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        json_encoders = {ObjectId: str}


class KnowledgeDocument(BaseModel):
    """MongoDB document model for knowledge base entries."""
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    title: str = Field(..., description="Document title")
    content: str = Field(..., description="Document content")
    source: str = Field(..., description="Source of document")
    document_type: str = Field(..., description="Type of document")
    embeddings: Optional[List[float]] = Field(None, description="Vector embeddings")
    metadata: Dict[str, Any] = Field(default={})
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        json_encoders = {ObjectId: str}


# WebSocket Models
class WebSocketMessage(BaseModel):
    """WebSocket message model."""
    type: str = Field(..., description="Message type")
    data: Dict[str, Any] = Field(..., description="Message data")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class WebSocketQueryUpdate(BaseModel):
    """WebSocket update for query status."""
    query_id: str = Field(..., description="Query ID")
    status: QueryStatus = Field(..., description="Current status")
    progress: float = Field(0.0, ge=0.0, le=1.0, description="Progress percentage")
    message: Optional[str] = Field(None, description="Status message")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


# Error Models
class ErrorResponse(BaseModel):
    """Standard error response model."""
    error: str = Field(..., description="Error type")
    message: str = Field(..., description="Error message")
    details: Optional[Dict[str, Any]] = Field(None, description="Additional details")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


# Health Check Models
class HealthStatus(BaseModel):
    """Health check response model."""
    status: str = Field(..., description="Service status")
    version: str = Field(..., description="Service version")
    timestamp: datetime = Field(default_factory=datetime.utcnow)
    mongodb: bool = Field(..., description="MongoDB connection status")
    backend_api: bool = Field(..., description="Spring Boot backend status")
    llm_provider: bool = Field(..., description="LLM provider status")
    agents: Dict[str, bool] = Field(..., description="Agent statuses")


# Batch Processing Models
class BatchQueryRequest(BaseModel):
    """Request model for batch query processing."""
    queries: List[QueryRequest] = Field(..., description="List of queries")
    batch_id: Optional[str] = Field(None, description="Batch ID")
    priority: ProcessingPriority = Field(ProcessingPriority.MEDIUM)


class BatchQueryResponse(BaseModel):
    """Response model for batch query processing."""
    batch_id: str = Field(..., description="Batch ID")
    total_queries: int = Field(..., description="Total number of queries")
    completed: int = Field(0, description="Number of completed queries")
    failed: int = Field(0, description="Number of failed queries")
    results: List[QueryResponse] = Field(default=[], description="Query results")
    status: str = Field("PROCESSING", description="Batch status")
    created_at: datetime = Field(default_factory=datetime.utcnow)
    completed_at: Optional[datetime] = Field(None)
