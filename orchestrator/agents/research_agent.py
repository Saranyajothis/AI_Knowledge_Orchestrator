"""
Research Agent for gathering and analyzing information.
Uses web search, document analysis, and RAG capabilities.
"""

import asyncio
from typing import Dict, Any, List, Optional
from datetime import datetime
import json

try:
    from langchain.agents import AgentExecutor, create_openai_tools_agent
except ImportError:
    # Fallback for different LangChain versions
    from langchain.agents import initialize_agent, AgentType
    AgentExecutor = None
    create_openai_tools_agent = None
from langchain.tools import Tool, DuckDuckGoSearchRun
from langchain_openai import ChatOpenAI
from langchain_community.tools import WikipediaQueryRun
from langchain_community.utilities import WikipediaAPIWrapper
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.schema import Document
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.embeddings import OpenAIEmbeddings
from langchain_community.vectorstores import Chroma
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings, logger
from models import AgentContext, AgentResponse, AgentType


class ResearchAgent:
    """
    Research Agent that gathers information from various sources.
    Capabilities:
    - Web search using DuckDuckGo
    - Wikipedia search for factual information
    - Document analysis and summarization
    - RAG-based retrieval from knowledge base
    """
    
    def __init__(self, mongodb_client=None, vector_store=None):
        """
        Initialize the Research Agent.
        
        Args:
            mongodb_client: MongoDB client for accessing knowledge base
            vector_store: Vector store for RAG capabilities
        """
        self.mongodb_client = mongodb_client
        self.vector_store = vector_store
        self.llm = self._initialize_llm()
        self.tools = self._initialize_tools()
        self.agent = self._create_agent()
        self.embeddings = None
        
        if settings.rag_enabled:
            self.embeddings = OpenAIEmbeddings(
                openai_api_key=settings.openai_api_key,
                model=settings.embedding_model
            )
            if not self.vector_store:
                self.vector_store = self._initialize_vector_store()
    
    def _initialize_llm(self) -> ChatOpenAI:
        """Initialize the language model based on settings."""
        llm_config = settings.get_llm_config()
        
        if settings.llm_provider == "openai":
            return ChatOpenAI(
                model=llm_config.get("model", "gpt-4-turbo-preview"),
                temperature=llm_config.get("temperature", 0.7),
                max_tokens=llm_config.get("max_tokens", 2000),
                openai_api_key=llm_config.get("api_key"),
                timeout=llm_config.get("timeout", 60),
            )
        else:
            # Add support for other providers (Anthropic, Ollama, etc.)
            raise NotImplementedError(f"Provider {settings.llm_provider} not implemented yet")
    
    def _initialize_tools(self) -> List[Tool]:
        """Initialize research tools."""
        tools = []
        
        # Web search tool
        if settings.research_enable_web_search:
            search = DuckDuckGoSearchRun()
            tools.append(
                Tool(
                    name="web_search",
                    description="Search the web for current information. Use for recent events, news, and general queries.",
                    func=search.run,
                )
            )
        
        # Wikipedia tool
        wikipedia = WikipediaQueryRun(api_wrapper=WikipediaAPIWrapper())
        tools.append(
            Tool(
                name="wikipedia",
                description="Search Wikipedia for factual, historical, and encyclopedic information.",
                func=wikipedia.run,
            )
        )
        
        # RAG tool for knowledge base
        if settings.rag_enabled:
            tools.append(
                Tool(
                    name="knowledge_base",
                    description="Search internal knowledge base for domain-specific information.",
                    func=self._search_knowledge_base,
                )
            )
        
        # Document analysis tool
        tools.append(
            Tool(
                name="analyze_document",
                description="Analyze and summarize document content.",
                func=self._analyze_document,
            )
        )
        
        return tools
    
    def _create_agent(self) -> AgentExecutor:
        """Create the LangChain agent."""
        # Define the prompt template
        prompt = ChatPromptTemplate.from_messages([
            ("system", """You are a research agent specializing in gathering and analyzing information.
            Your goal is to provide comprehensive, accurate, and well-sourced information.
            
            Guidelines:
            1. Use multiple sources when possible to verify information
            2. Cite your sources clearly
            3. Distinguish between facts and opinions
            4. Provide balanced perspectives on controversial topics
            5. Acknowledge when information is uncertain or incomplete
            
            When answering:
            - Start with a clear summary
            - Provide detailed information with sources
            - End with key takeaways or recommendations
            """),
            ("human", "{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ])
        
        # Create the agent
        agent = create_openai_tools_agent(
            llm=self.llm,
            tools=self.tools,
            prompt=prompt
        )
        
        # Create agent executor
        return AgentExecutor(
            agent=agent,
            tools=self.tools,
            verbose=settings.agent_verbose,
            max_iterations=settings.agent_max_iterations,
            handle_parsing_errors=True,
            return_intermediate_steps=True,
        )
    
    def _initialize_vector_store(self) -> Chroma:
        """Initialize the vector store for RAG."""
        return Chroma(
            persist_directory=settings.chroma_persist_dir,
            embedding_function=self.embeddings,
            collection_name="knowledge_base"
        )
    
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=4, max=10)
    )
    async def _search_knowledge_base(self, query: str) -> str:
        """
        Search the knowledge base using RAG.
        
        Args:
            query: Search query
            
        Returns:
            Relevant information from knowledge base
        """
        try:
            if not self.vector_store:
                return "Knowledge base not available"
            
            # Perform similarity search
            docs = self.vector_store.similarity_search(
                query,
                k=settings.rag_top_k
            )
            
            if not docs:
                return "No relevant information found in knowledge base"
            
            # Format results
            results = []
            for i, doc in enumerate(docs, 1):
                source = doc.metadata.get("source", "Unknown")
                content = doc.page_content[:500]  # Limit content length
                results.append(f"{i}. Source: {source}\n{content}...")
            
            return "\n\n".join(results)
            
        except Exception as e:
            logger.error(f"Error searching knowledge base: {e}")
            return f"Error searching knowledge base: {str(e)}"
    
    async def _analyze_document(self, content: str) -> str:
        """
        Analyze and summarize document content.
        
        Args:
            content: Document content to analyze
            
        Returns:
            Document analysis and summary
        """
        try:
            # Use LLM to analyze the document
            analysis_prompt = f"""
            Analyze the following document and provide:
            1. Main topics covered
            2. Key points and findings
            3. Summary of conclusions
            4. Notable insights or recommendations
            
            Document:
            {content[:3000]}  # Limit content for token constraints
            """
            
            response = await self.llm.ainvoke(analysis_prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error analyzing document: {e}")
            return f"Error analyzing document: {str(e)}"
    
    async def process(self, context: AgentContext) -> AgentResponse:
        """
        Process a research query.
        
        Args:
            context: Agent context with query information
            
        Returns:
            Agent response with research results
        """
        start_time = datetime.utcnow()
        
        try:
            logger.info(f"Research agent processing query: {context.query_id}")
            
            # Enhance query with research-specific instructions
            enhanced_query = f"""
            Research Query: {context.original_query}
            
            Please provide comprehensive research on this topic.
            Include multiple perspectives and cite sources where possible.
            Focus on accuracy and relevance.
            """
            
            # Execute the agent
            result = await self.agent.ainvoke({
                "input": enhanced_query
            })
            
            # Extract sources from intermediate steps
            sources = self._extract_sources(result.get("intermediate_steps", []))
            
            # Calculate processing time
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Create response
            response = AgentResponse(
                agent_type=AgentType.RESEARCH,
                content=result["output"],
                confidence=self._calculate_confidence(result),
                processing_time=processing_time,
                sources=sources,
                metadata={
                    "tools_used": self._get_tools_used(result),
                    "iterations": len(result.get("intermediate_steps", [])),
                }
            )
            
            logger.info(f"Research agent completed query: {context.query_id}")
            return response
            
        except Exception as e:
            logger.error(f"Error in research agent: {e}")
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            return AgentResponse(
                agent_type=AgentType.RESEARCH,
                content="Failed to complete research",
                confidence=0.0,
                processing_time=processing_time,
                error=str(e),
                metadata={"error_type": type(e).__name__}
            )
    
    def _extract_sources(self, intermediate_steps: List) -> List[str]:
        """Extract sources from intermediate steps."""
        sources = []
        
        for step in intermediate_steps:
            if len(step) >= 2:
                tool_name = step[0].tool if hasattr(step[0], 'tool') else None
                if tool_name:
                    sources.append(f"{tool_name}: {step[0].tool_input[:100]}...")
        
        return sources
    
    def _calculate_confidence(self, result: Dict) -> float:
        """Calculate confidence score based on results."""
        # Base confidence
        confidence = 0.5
        
        # Increase confidence based on number of sources
        steps = result.get("intermediate_steps", [])
        if steps:
            confidence += min(0.3, len(steps) * 0.1)
        
        # Increase confidence if output is substantial
        output = result.get("output", "")
        if len(output) > 500:
            confidence += 0.2
        
        return min(1.0, confidence)
    
    def _get_tools_used(self, result: Dict) -> List[str]:
        """Get list of tools used in the research."""
        tools_used = []
        
        for step in result.get("intermediate_steps", []):
            if len(step) >= 1 and hasattr(step[0], 'tool'):
                tool_name = step[0].tool
                if tool_name not in tools_used:
                    tools_used.append(tool_name)
        
        return tools_used
    
    async def add_to_knowledge_base(self, documents: List[Dict[str, Any]]) -> bool:
        """
        Add documents to the knowledge base.
        
        Args:
            documents: List of documents to add
            
        Returns:
            Success status
        """
        try:
            if not self.vector_store or not settings.rag_enabled:
                logger.warning("RAG not enabled or vector store not initialized")
                return False
            
            # Convert to LangChain documents
            docs = []
            for doc in documents:
                docs.append(Document(
                    page_content=doc.get("content", ""),
                    metadata={
                        "source": doc.get("source", "unknown"),
                        "title": doc.get("title", ""),
                        "timestamp": datetime.utcnow().isoformat(),
                        **doc.get("metadata", {})
                    }
                ))
            
            # Split documents if needed
            text_splitter = RecursiveCharacterTextSplitter(
                chunk_size=settings.rag_chunk_size,
                chunk_overlap=settings.rag_chunk_overlap
            )
            split_docs = text_splitter.split_documents(docs)
            
            # Add to vector store
            self.vector_store.add_documents(split_docs)
            
            logger.info(f"Added {len(split_docs)} document chunks to knowledge base")
            return True
            
        except Exception as e:
            logger.error(f"Error adding to knowledge base: {e}")
            return False
    
    def get_status(self) -> Dict[str, Any]:
        """Get agent status."""
        return {
            "agent": "research",
            "status": "active",
            "llm_provider": settings.llm_provider,
            "tools_available": [tool.name for tool in self.tools],
            "rag_enabled": settings.rag_enabled,
            "vector_store_initialized": self.vector_store is not None,
        }
