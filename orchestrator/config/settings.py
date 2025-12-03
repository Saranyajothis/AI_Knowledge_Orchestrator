"""
Configuration settings for the AI Knowledge Orchestrator.
Manages environment variables and application settings.
"""

import os
from typing import Optional, Dict, Any
from pydantic_settings import BaseSettings
from pydantic import Field, validator
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()


class Settings(BaseSettings):
    """Application settings managed by Pydantic."""
    
    # Application Settings
    app_name: str = "AI Knowledge Orchestrator"
    app_version: str = "1.0.0"
    debug: bool = Field(default=False, env="DEBUG")
    environment: str = Field(default="development", env="ENVIRONMENT")
    
    # MongoDB Settings
    mongodb_uri: str = Field(
        default="mongodb://localhost:27017/ai_orchestrator",
        env="MONGODB_URI"
    )
    mongodb_database: str = Field(
        default="ai_orchestrator",
        env="MONGODB_DATABASE"
    )
    mongodb_max_pool_size: int = Field(default=10, env="MONGODB_MAX_POOL_SIZE")
    mongodb_min_pool_size: int = Field(default=1, env="MONGODB_MIN_POOL_SIZE")
    
    # Spring Boot Backend API
    backend_api_url: str = Field(
        default="http://localhost:8080",
        env="BACKEND_API_URL"
    )
    backend_api_timeout: int = Field(default=30, env="BACKEND_API_TIMEOUT")
    backend_api_key: Optional[str] = Field(default=None, env="BACKEND_API_KEY")
    
    # LLM Provider Settings
    llm_provider: str = Field(default="openai", env="LLM_PROVIDER")  # openai, anthropic, ollama
    llm_model: str = Field(default="gpt-4-turbo-preview", env="LLM_MODEL")
    llm_temperature: float = Field(default=0.7, env="LLM_TEMPERATURE")
    llm_max_tokens: int = Field(default=2000, env="LLM_MAX_TOKENS")
    llm_timeout: int = Field(default=60, env="LLM_TIMEOUT")
    
    # OpenAI Settings
    openai_api_key: Optional[str] = Field(default=None, env="OPENAI_API_KEY")
    openai_org_id: Optional[str] = Field(default=None, env="OPENAI_ORG_ID")
    openai_base_url: Optional[str] = Field(default=None, env="OPENAI_BASE_URL")
    
    # Anthropic Settings
    anthropic_api_key: Optional[str] = Field(default=None, env="ANTHROPIC_API_KEY")
    
    # Ollama Settings
    ollama_base_url: str = Field(default="http://localhost:11434", env="OLLAMA_BASE_URL")
    ollama_model: str = Field(default="llama2", env="OLLAMA_MODEL")
    
    # Agent Settings
    agent_max_iterations: int = Field(default=10, env="AGENT_MAX_ITERATIONS")
    agent_timeout: int = Field(default=300, env="AGENT_TIMEOUT")  # 5 minutes
    agent_verbose: bool = Field(default=True, env="AGENT_VERBOSE")
    
    # Research Agent Settings
    research_search_results: int = Field(default=5, env="RESEARCH_SEARCH_RESULTS")
    research_enable_web_search: bool = Field(default=True, env="RESEARCH_ENABLE_WEB_SEARCH")
    
    # Code Agent Settings
    code_execution_enabled: bool = Field(default=False, env="CODE_EXECUTION_ENABLED")
    code_max_lines: int = Field(default=500, env="CODE_MAX_LINES")
    
    # RAG Settings
    rag_enabled: bool = Field(default=True, env="RAG_ENABLED")
    rag_chunk_size: int = Field(default=1000, env="RAG_CHUNK_SIZE")
    rag_chunk_overlap: int = Field(default=200, env="RAG_CHUNK_OVERLAP")
    rag_top_k: int = Field(default=5, env="RAG_TOP_K")
    embedding_model: str = Field(default="text-embedding-ada-002", env="EMBEDDING_MODEL")
    
    # Vector Store Settings
    vector_store_type: str = Field(default="chroma", env="VECTOR_STORE_TYPE")  # chroma, faiss, mongodb
    chroma_persist_dir: str = Field(default="./chroma_db", env="CHROMA_PERSIST_DIR")
    
    # FastAPI Settings
    api_host: str = Field(default="0.0.0.0", env="API_HOST")
    api_port: int = Field(default=8000, env="API_PORT")
    api_workers: int = Field(default=1, env="API_WORKERS")
    api_reload: bool = Field(default=True, env="API_RELOAD")
    
    # Logging Settings
    log_level: str = Field(default="INFO", env="LOG_LEVEL")
    log_format: str = Field(
        default="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan> - <level>{message}</level>",
        env="LOG_FORMAT"
    )
    log_file: Optional[str] = Field(default="orchestrator.log", env="LOG_FILE")
    
    # Cache Settings
    cache_enabled: bool = Field(default=True, env="CACHE_ENABLED")
    cache_ttl: int = Field(default=3600, env="CACHE_TTL")  # 1 hour
    
    # Retry Settings
    retry_max_attempts: int = Field(default=3, env="RETRY_MAX_ATTEMPTS")
    retry_delay: int = Field(default=1, env="RETRY_DELAY")
    retry_backoff: int = Field(default=2, env="RETRY_BACKOFF")
    
    @validator("llm_provider")
    def validate_llm_provider(cls, v):
        """Validate LLM provider is supported."""
        valid_providers = ["openai", "anthropic", "ollama", "azure"]
        if v not in valid_providers:
            raise ValueError(f"LLM provider must be one of {valid_providers}")
        return v
    
    @validator("vector_store_type")
    def validate_vector_store(cls, v):
        """Validate vector store type is supported."""
        valid_stores = ["chroma", "faiss", "mongodb", "pinecone"]
        if v not in valid_stores:
            raise ValueError(f"Vector store must be one of {valid_stores}")
        return v
    
    @validator("openai_api_key")
    def validate_openai_key(cls, v, values):
        """Validate OpenAI API key is provided when using OpenAI."""
        if values.get("llm_provider") == "openai" and not v:
            raise ValueError("OpenAI API key is required when using OpenAI provider")
        return v
    
    def get_llm_config(self) -> Dict[str, Any]:
        """Get LLM configuration based on provider."""
        base_config = {
            "temperature": self.llm_temperature,
            "max_tokens": self.llm_max_tokens,
            "timeout": self.llm_timeout,
        }
        
        if self.llm_provider == "openai":
            return {
                **base_config,
                "api_key": self.openai_api_key,
                "model": self.llm_model,
                "organization": self.openai_org_id,
                "base_url": self.openai_base_url,
            }
        elif self.llm_provider == "anthropic":
            return {
                **base_config,
                "api_key": self.anthropic_api_key,
                "model": self.llm_model,
            }
        elif self.llm_provider == "ollama":
            return {
                **base_config,
                "base_url": self.ollama_base_url,
                "model": self.ollama_model,
            }
        else:
            raise ValueError(f"Unsupported LLM provider: {self.llm_provider}")
    
    def get_mongodb_url(self) -> str:
        """Get MongoDB connection URL."""
        return self.mongodb_uri
    
    def is_production(self) -> bool:
        """Check if running in production environment."""
        return self.environment.lower() == "production"
    
    def is_development(self) -> bool:
        """Check if running in development environment."""
        return self.environment.lower() == "development"
    
    class Config:
        """Pydantic configuration."""
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# Global settings instance
settings = Settings()


# Export commonly used settings
def get_settings() -> Settings:
    """Get application settings instance."""
    return settings


# Logging configuration
def setup_logging():
    """Configure application logging."""
    from loguru import logger
    import sys
    
    # Remove default logger
    logger.remove()
    
    # Add console logger
    logger.add(
        sys.stdout,
        format=settings.log_format,
        level=settings.log_level,
        colorize=True,
    )
    
    # Add file logger if specified
    if settings.log_file:
        logger.add(
            settings.log_file,
            format=settings.log_format,
            level=settings.log_level,
            rotation="10 MB",
            retention="7 days",
            compression="zip",
        )
    
    return logger


# Initialize logger
logger = setup_logging()
