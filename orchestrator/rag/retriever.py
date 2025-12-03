"""
Retriever service for RAG functionality.
Handles document retrieval and similarity search.
"""

from typing import List, Dict, Any, Optional
from datetime import datetime

from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.schema import Document
from langchain_community.vectorstores import Chroma
from langchain.retrievers import ContextualCompressionRetriever
from langchain.retrievers.document_compressors import LLMChainExtractor

# Try to import FAISS, but don't fail if not available
try:
    from langchain_community.vectorstores import FAISS
    FAISS_AVAILABLE = True
except ImportError:
    FAISS = None
    FAISS_AVAILABLE = False

from config import settings, logger
from .embeddings import EmbeddingService


class RetrieverService:
    """Service for document retrieval and RAG operations."""
    
    def __init__(self, mongodb_client=None):
        """
        Initialize the retriever service.
        
        Args:
            mongodb_client: MongoDB client for document storage
        """
        self.mongodb_client = mongodb_client
        self.embedding_service = EmbeddingService()
        self.vector_store = self._initialize_vector_store()
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.rag_chunk_size,
            chunk_overlap=settings.rag_chunk_overlap,
            length_function=len,
        )
    
    def _initialize_vector_store(self):
        """Initialize the vector store based on configuration."""
        try:
            embeddings = self.embedding_service.embeddings
            
            if settings.vector_store_type == "chroma":
                return Chroma(
                    persist_directory=settings.chroma_persist_dir,
                    embedding_function=embeddings,
                    collection_name="knowledge_base"
                )
            elif settings.vector_store_type == "faiss":
                if not FAISS_AVAILABLE:
                    logger.warning("FAISS not available, falling back to Chroma")
                    return Chroma(
                        persist_directory=settings.chroma_persist_dir,
                        embedding_function=embeddings,
                        collection_name="knowledge_base"
                    )
                # Initialize or load FAISS index
                try:
                    return FAISS.load_local(
                        "./faiss_index",
                        embeddings
                    )
                except:
                    # Create new FAISS index if doesn't exist
                    return FAISS.from_texts(
                        ["Initial document"],
                        embeddings,
                        metadatas=[{"source": "init"}]
                    )
            else:
                # Default to Chroma
                return Chroma(
                    persist_directory=settings.chroma_persist_dir,
                    embedding_function=embeddings,
                    collection_name="knowledge_base"
                )
        except Exception as e:
            logger.error(f"Failed to initialize vector store: {e}")
            return None
    
    async def add_documents(
        self,
        documents: List[Dict[str, Any]],
        split: bool = True
    ) -> bool:
        """
        Add documents to the vector store.
        
        Args:
            documents: List of documents to add
            split: Whether to split documents into chunks
            
        Returns:
            Success status
        """
        try:
            if not self.vector_store:
                logger.error("Vector store not initialized")
                return False
            
            # Convert to LangChain documents
            langchain_docs = []
            for doc in documents:
                langchain_docs.append(Document(
                    page_content=doc.get("content", ""),
                    metadata={
                        "source": doc.get("source", "unknown"),
                        "title": doc.get("title", ""),
                        "timestamp": datetime.utcnow().isoformat(),
                        **doc.get("metadata", {})
                    }
                ))
            
            # Split documents if requested
            if split:
                langchain_docs = self.text_splitter.split_documents(langchain_docs)
            
            # Add to vector store
            self.vector_store.add_documents(langchain_docs)
            
            # Save if using FAISS
            if settings.vector_store_type == "faiss":
                self.vector_store.save_local("./faiss_index")
            
            logger.info(f"Added {len(langchain_docs)} document chunks to vector store")
            return True
            
        except Exception as e:
            logger.error(f"Error adding documents to vector store: {e}")
            return False
    
    async def retrieve(
        self,
        query: str,
        k: int = None,
        filter_dict: Optional[Dict[str, Any]] = None
    ) -> List[Document]:
        """
        Retrieve relevant documents for a query.
        
        Args:
            query: Search query
            k: Number of documents to retrieve
            filter_dict: Optional metadata filters
            
        Returns:
            List of relevant documents
        """
        try:
            if not self.vector_store:
                logger.error("Vector store not initialized")
                return []
            
            k = k or settings.rag_top_k
            
            # Perform similarity search
            if filter_dict:
                docs = self.vector_store.similarity_search(
                    query,
                    k=k,
                    filter=filter_dict
                )
            else:
                docs = self.vector_store.similarity_search(query, k=k)
            
            return docs
            
        except Exception as e:
            logger.error(f"Error retrieving documents: {e}")
            return []
    
    async def retrieve_with_scores(
        self,
        query: str,
        k: int = None
    ) -> List[tuple[Document, float]]:
        """
        Retrieve documents with similarity scores.
        
        Args:
            query: Search query
            k: Number of documents to retrieve
            
        Returns:
            List of (document, score) tuples
        """
        try:
            if not self.vector_store:
                return []
            
            k = k or settings.rag_top_k
            
            # Perform similarity search with scores
            docs_with_scores = self.vector_store.similarity_search_with_score(
                query, k=k
            )
            
            return docs_with_scores
            
        except Exception as e:
            logger.error(f"Error retrieving documents with scores: {e}")
            return []
    
    async def hybrid_search(
        self,
        query: str,
        k: int = None
    ) -> List[Document]:
        """
        Perform hybrid search combining vector and keyword search.
        
        Args:
            query: Search query
            k: Number of documents to retrieve
            
        Returns:
            List of relevant documents
        """
        try:
            k = k or settings.rag_top_k
            
            # Vector search
            vector_docs = await self.retrieve(query, k=k)
            
            # Keyword search from MongoDB (if available)
            keyword_docs = []
            if self.mongodb_client:
                collection = self.mongodb_client.db.knowledge
                
                # Text search
                results = await collection.find(
                    {"$text": {"$search": query}},
                    {"score": {"$meta": "textScore"}}
                ).sort(
                    [("score", {"$meta": "textScore"})]
                ).limit(k).to_list(length=k)
                
                for result in results:
                    keyword_docs.append(Document(
                        page_content=result.get("content", ""),
                        metadata={
                            "source": result.get("source", "unknown"),
                            "title": result.get("title", ""),
                            "score": result.get("score", 0),
                        }
                    ))
            
            # Combine and deduplicate
            all_docs = vector_docs + keyword_docs
            seen = set()
            unique_docs = []
            
            for doc in all_docs:
                doc_id = doc.metadata.get("source", "") + doc.page_content[:100]
                if doc_id not in seen:
                    seen.add(doc_id)
                    unique_docs.append(doc)
            
            return unique_docs[:k]
            
        except Exception as e:
            logger.error(f"Error in hybrid search: {e}")
            return []
    
    def get_retriever(self, k: int = None):
        """
        Get a LangChain retriever instance.
        
        Args:
            k: Number of documents to retrieve
            
        Returns:
            Retriever instance
        """
        if not self.vector_store:
            return None
        
        k = k or settings.rag_top_k
        return self.vector_store.as_retriever(search_kwargs={"k": k})
    
    async def clear_vector_store(self) -> bool:
        """
        Clear all documents from the vector store.
        
        Returns:
            Success status
        """
        try:
            if settings.vector_store_type == "chroma":
                # Clear Chroma collection
                self.vector_store.delete_collection()
                self.vector_store = self._initialize_vector_store()
            elif settings.vector_store_type == "faiss" and FAISS_AVAILABLE:
                # Reinitialize FAISS
                self.vector_store = FAISS.from_texts(
                    ["Initial document"],
                    self.embedding_service.embeddings,
                    metadatas=[{"source": "init"}]
                )
                self.vector_store.save_local("./faiss_index")
            else:
                # Default to reinitializing Chroma
                self.vector_store = self._initialize_vector_store()
            
            logger.info("Vector store cleared successfully")
            return True
            
        except Exception as e:
            logger.error(f"Error clearing vector store: {e}")
            return False
    
    def get_stats(self) -> Dict[str, Any]:
        """
        Get vector store statistics.
        
        Returns:
            Statistics dictionary
        """
        try:
            stats = {
                "vector_store_type": settings.vector_store_type,
                "embedding_model": settings.embedding_model,
                "chunk_size": settings.rag_chunk_size,
                "chunk_overlap": settings.rag_chunk_overlap,
            }
            
            if self.vector_store:
                if settings.vector_store_type == "chroma":
                    # Get collection stats
                    collection = self.vector_store._collection
                    stats["document_count"] = collection.count()
                elif settings.vector_store_type == "faiss" and FAISS_AVAILABLE:
                    # Get FAISS stats
                    stats["document_count"] = self.vector_store.index.ntotal
                else:
                    stats["document_count"] = "Unknown"
            
            return stats
            
        except Exception as e:
            logger.error(f"Error getting vector store stats: {e}")
            return {"error": str(e)}
