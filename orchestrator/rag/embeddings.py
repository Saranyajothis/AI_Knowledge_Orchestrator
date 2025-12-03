"""
Embeddings service for converting text to vector representations.
"""

from typing import List, Dict, Any
import numpy as np
from langchain_openai import OpenAIEmbeddings
from langchain_community.embeddings import HuggingFaceEmbeddings, OllamaEmbeddings

from config import settings, logger


class EmbeddingService:
    """Service for generating text embeddings."""
    
    def __init__(self):
        """Initialize the embedding service."""
        self.embeddings = self._initialize_embeddings()
    
    def _initialize_embeddings(self):
        """Initialize embeddings based on configuration."""
        try:
            if settings.llm_provider == "openai":
                return OpenAIEmbeddings(
                    openai_api_key=settings.openai_api_key,
                    model=settings.embedding_model
                )
            elif settings.llm_provider == "ollama":
                return OllamaEmbeddings(
                    base_url=settings.ollama_base_url,
                    model=settings.ollama_model
                )
            else:
                # Fallback to HuggingFace embeddings
                return HuggingFaceEmbeddings(
                    model_name="sentence-transformers/all-MiniLM-L6-v2"
                )
        except Exception as e:
            logger.error(f"Failed to initialize embeddings: {e}")
            # Use HuggingFace as fallback
            return HuggingFaceEmbeddings(
                model_name="sentence-transformers/all-MiniLM-L6-v2"
            )
    
    async def embed_text(self, text: str) -> List[float]:
        """
        Generate embedding for a single text.
        
        Args:
            text: Text to embed
            
        Returns:
            Embedding vector
        """
        try:
            embedding = await self.embeddings.aembed_query(text)
            return embedding
        except Exception as e:
            logger.error(f"Error generating embedding: {e}")
            return []
    
    async def embed_documents(self, documents: List[str]) -> List[List[float]]:
        """
        Generate embeddings for multiple documents.
        
        Args:
            documents: List of documents to embed
            
        Returns:
            List of embedding vectors
        """
        try:
            embeddings = await self.embeddings.aembed_documents(documents)
            return embeddings
        except Exception as e:
            logger.error(f"Error generating document embeddings: {e}")
            return []
    
    def compute_similarity(self, embedding1: List[float], embedding2: List[float]) -> float:
        """
        Compute cosine similarity between two embeddings.
        
        Args:
            embedding1: First embedding vector
            embedding2: Second embedding vector
            
        Returns:
            Cosine similarity score
        """
        try:
            # Convert to numpy arrays
            vec1 = np.array(embedding1)
            vec2 = np.array(embedding2)
            
            # Compute cosine similarity
            similarity = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))
            return float(similarity)
        except Exception as e:
            logger.error(f"Error computing similarity: {e}")
            return 0.0
