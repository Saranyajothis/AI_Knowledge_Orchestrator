"""
Decision Agent for routing queries and synthesizing responses.
Coordinates between different agents and makes strategic decisions.
"""

import asyncio
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime
import json

from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain.tools import Tool
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.schema import BaseMessage, HumanMessage, SystemMessage
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings, logger
from models import (
    AgentContext, 
    AgentResponse, 
    AgentType,
    QueryType,
    ProcessingPriority
)


class DecisionAgent:
    """
    Decision Agent that routes queries and synthesizes responses.
    Capabilities:
    - Query classification and routing
    - Multi-agent coordination
    - Response synthesis and ranking
    - Confidence scoring
    - Priority management
    """
    
    def __init__(self, research_agent=None, code_agent=None):
        """
        Initialize the Decision Agent.
        
        Args:
            research_agent: Research agent instance
            code_agent: Code agent instance
        """
        self.research_agent = research_agent
        self.code_agent = code_agent
        self.llm = self._initialize_llm()
        self.tools = self._initialize_tools()
        self.agent = self._create_agent()
        
        # Agent routing rules
        self.routing_rules = {
            QueryType.RESEARCH: [AgentType.RESEARCH],
            QueryType.CODE: [AgentType.CODE],
            QueryType.DECISION: [AgentType.RESEARCH, AgentType.CODE],
            QueryType.GENERAL: [],  # Determined dynamically
        }
    
    def _initialize_llm(self) -> ChatOpenAI:
        """Initialize the language model based on settings."""
        llm_config = settings.get_llm_config()
        
        if settings.llm_provider == "openai":
            return ChatOpenAI(
                model=llm_config.get("model", "gpt-4-turbo-preview"),
                temperature=0.5,  # Balanced temperature for decision making
                max_tokens=llm_config.get("max_tokens", 2000),
                openai_api_key=llm_config.get("api_key"),
                timeout=llm_config.get("timeout", 60),
            )
        else:
            raise NotImplementedError(f"Provider {settings.llm_provider} not implemented yet")
    
    def _initialize_tools(self) -> List[Tool]:
        """Initialize decision-making tools."""
        tools = []
        
        # Query classification tool
        tools.append(
            Tool(
                name="classify_query",
                description="Classify the type and intent of a query",
                func=self._classify_query,
            )
        )
        
        # Agent selection tool
        tools.append(
            Tool(
                name="select_agents",
                description="Select appropriate agents for processing",
                func=self._select_agents,
            )
        )
        
        # Response synthesis tool
        tools.append(
            Tool(
                name="synthesize_response",
                description="Synthesize multiple agent responses into a coherent answer",
                func=self._synthesize_responses,
            )
        )
        
        # Priority assessment tool
        tools.append(
            Tool(
                name="assess_priority",
                description="Assess the priority level of a query",
                func=self._assess_priority,
            )
        )
        
        # Confidence calculation tool
        tools.append(
            Tool(
                name="calculate_confidence",
                description="Calculate overall confidence score",
                func=self._calculate_overall_confidence,
            )
        )
        
        return tools
    
    def _create_agent(self) -> AgentExecutor:
        """Create the LangChain agent."""
        # Define the prompt template
        prompt = ChatPromptTemplate.from_messages([
            ("system", """You are a decision agent responsible for coordinating and routing queries.
            Your role is to:
            1. Understand the user's intent and requirements
            2. Classify queries appropriately
            3. Select the best agents for processing
            4. Synthesize responses from multiple agents
            5. Ensure comprehensive and accurate answers
            
            Guidelines:
            - Analyze queries carefully before routing
            - Consider using multiple agents for complex queries
            - Prioritize accuracy and completeness
            - Provide clear, structured responses
            - Acknowledge limitations when present
            
            Available agents:
            - Research Agent: For information gathering, web search, and analysis
            - Code Agent: For programming, technical solutions, and code analysis
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
    
    async def _classify_query(self, query: str) -> str:
        """
        Classify the type and intent of a query.
        
        Args:
            query: User query
            
        Returns:
            Classification result
        """
        try:
            prompt = f"""
            Classify the following query into categories and identify the user's intent:
            
            Query: {query}
            
            Provide:
            1. Primary category (RESEARCH, CODE, DECISION, or GENERAL)
            2. Specific intent
            3. Key topics/entities
            4. Required capabilities
            5. Complexity level (simple, moderate, complex)
            
            Format as JSON.
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error classifying query: {e}")
            return json.dumps({
                "category": "GENERAL",
                "intent": "unknown",
                "error": str(e)
            })
    
    async def _select_agents(self, query_analysis: str) -> str:
        """
        Select appropriate agents based on query analysis.
        
        Args:
            query_analysis: Analysis of the query
            
        Returns:
            Selected agents
        """
        try:
            # Parse the analysis
            analysis = json.loads(query_analysis) if isinstance(query_analysis, str) else query_analysis
            
            category = analysis.get("category", "GENERAL")
            complexity = analysis.get("complexity", "moderate")
            
            selected_agents = []
            
            # Apply routing rules
            if category == "RESEARCH":
                selected_agents.append("research")
            elif category == "CODE":
                selected_agents.append("code")
            elif category == "DECISION":
                # Use both agents for decision queries
                selected_agents.extend(["research", "code"])
            else:  # GENERAL
                # Decide based on content
                if "code" in query_analysis.lower() or "program" in query_analysis.lower():
                    selected_agents.append("code")
                if "research" in query_analysis.lower() or "find" in query_analysis.lower():
                    selected_agents.append("research")
                
                # If no specific indicators, use research as default
                if not selected_agents:
                    selected_agents.append("research")
            
            return json.dumps({
                "agents": selected_agents,
                "reason": f"Selected based on {category} category and {complexity} complexity"
            })
            
        except Exception as e:
            logger.error(f"Error selecting agents: {e}")
            return json.dumps({
                "agents": ["research"],  # Default to research
                "error": str(e)
            })
    
    async def _synthesize_responses(self, responses: str) -> str:
        """
        Synthesize multiple agent responses into a coherent answer.
        
        Args:
            responses: JSON string of agent responses
            
        Returns:
            Synthesized response
        """
        try:
            # Parse responses
            agent_responses = json.loads(responses) if isinstance(responses, str) else responses
            
            if not agent_responses:
                return "No agent responses to synthesize"
            
            # Build synthesis prompt
            prompt = f"""
            Synthesize the following agent responses into a comprehensive answer:
            
            {json.dumps(agent_responses, indent=2)}
            
            Create a unified response that:
            1. Combines key information from all agents
            2. Maintains accuracy and coherence
            3. Eliminates redundancy
            4. Preserves important details and sources
            5. Provides a clear structure
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error synthesizing responses: {e}")
            return f"Error synthesizing responses: {str(e)}"
    
    async def _assess_priority(self, query: str) -> str:
        """
        Assess the priority level of a query.
        
        Args:
            query: User query
            
        Returns:
            Priority assessment
        """
        try:
            prompt = f"""
            Assess the priority level of the following query:
            
            Query: {query}
            
            Consider:
            1. Urgency indicators
            2. Business impact
            3. User context
            4. Query complexity
            
            Assign priority: LOW, MEDIUM, HIGH, or CRITICAL
            Provide reasoning.
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error assessing priority: {e}")
            return "MEDIUM"  # Default priority
    
    def _calculate_overall_confidence(self, responses: str) -> str:
        """
        Calculate overall confidence score from agent responses.
        
        Args:
            responses: Agent responses
            
        Returns:
            Confidence score as string
        """
        try:
            # Parse responses
            agent_responses = json.loads(responses) if isinstance(responses, str) else responses
            
            if not agent_responses:
                return "0.0"
            
            # Calculate weighted average confidence
            total_confidence = 0.0
            total_weight = 0.0
            
            for response in agent_responses:
                confidence = response.get("confidence", 0.5)
                # Weight by response length (simple heuristic)
                weight = min(1.0, len(response.get("content", "")) / 1000)
                total_confidence += confidence * weight
                total_weight += weight
            
            if total_weight > 0:
                overall_confidence = total_confidence / total_weight
            else:
                overall_confidence = 0.5
            
            return str(round(overall_confidence, 2))
            
        except Exception as e:
            logger.error(f"Error calculating confidence: {e}")
            return "0.5"
    
    async def process(self, context: AgentContext) -> AgentResponse:
        """
        Process a query by routing to appropriate agents and synthesizing responses.
        
        Args:
            context: Agent context with query information
            
        Returns:
            Synthesized agent response
        """
        start_time = datetime.utcnow()
        
        try:
            logger.info(f"Decision agent processing query: {context.query_id}")
            
            # Step 1: Classify the query
            classification = await self._classify_query(context.original_query)
            
            # Step 2: Select appropriate agents
            agent_selection = await self._select_agents(classification)
            selected_agents = json.loads(agent_selection).get("agents", ["research"])
            
            # Step 3: Route to selected agents and collect responses
            agent_responses = []
            
            for agent_name in selected_agents:
                if agent_name == "research" and self.research_agent:
                    logger.debug(f"Routing to research agent for query: {context.query_id}")
                    response = await self.research_agent.process(context)
                    agent_responses.append({
                        "agent": "research",
                        "content": response.content,
                        "confidence": response.confidence,
                        "processing_time": response.processing_time,
                        "sources": response.sources,
                    })
                
                elif agent_name == "code" and self.code_agent:
                    logger.debug(f"Routing to code agent for query: {context.query_id}")
                    response = await self.code_agent.process(context)
                    agent_responses.append({
                        "agent": "code",
                        "content": response.content,
                        "confidence": response.confidence,
                        "processing_time": response.processing_time,
                        "metadata": response.metadata,
                    })
            
            # Step 4: Synthesize responses if multiple agents were used
            if len(agent_responses) > 1:
                synthesized_content = await self._synthesize_responses(json.dumps(agent_responses))
            elif agent_responses:
                synthesized_content = agent_responses[0]["content"]
            else:
                synthesized_content = "No agents available to process this query."
            
            # Step 5: Calculate overall confidence
            overall_confidence = float(self._calculate_overall_confidence(json.dumps(agent_responses)))
            
            # Calculate total processing time
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Create final response
            response = AgentResponse(
                agent_type=AgentType.DECISION,
                content=synthesized_content,
                confidence=overall_confidence,
                processing_time=processing_time,
                metadata={
                    "classification": json.loads(classification) if isinstance(classification, str) else classification,
                    "agents_used": selected_agents,
                    "agent_responses": len(agent_responses),
                }
            )
            
            logger.info(f"Decision agent completed query: {context.query_id}")
            return response
            
        except Exception as e:
            logger.error(f"Error in decision agent: {e}")
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            return AgentResponse(
                agent_type=AgentType.DECISION,
                content="Failed to process query through decision agent",
                confidence=0.0,
                processing_time=processing_time,
                error=str(e),
                metadata={"error_type": type(e).__name__}
            )
    
    async def route_query(self, query: str, query_type: QueryType) -> Tuple[List[AgentType], ProcessingPriority]:
        """
        Route a query to appropriate agents.
        
        Args:
            query: User query
            query_type: Type of query
            
        Returns:
            Tuple of (selected agents, priority)
        """
        try:
            # Classify the query
            classification = await self._classify_query(query)
            
            # Assess priority
            priority_assessment = await self._assess_priority(query)
            
            # Determine priority
            if "CRITICAL" in priority_assessment.upper():
                priority = ProcessingPriority.CRITICAL
            elif "HIGH" in priority_assessment.upper():
                priority = ProcessingPriority.HIGH
            elif "LOW" in priority_assessment.upper():
                priority = ProcessingPriority.LOW
            else:
                priority = ProcessingPriority.MEDIUM
            
            # Select agents
            if query_type in self.routing_rules:
                agent_types = self.routing_rules[query_type]
            else:
                # Dynamic selection for general queries
                agent_selection = await self._select_agents(classification)
                selected = json.loads(agent_selection).get("agents", ["research"])
                
                agent_types = []
                for agent_name in selected:
                    if agent_name == "research":
                        agent_types.append(AgentType.RESEARCH)
                    elif agent_name == "code":
                        agent_types.append(AgentType.CODE)
            
            return agent_types, priority
            
        except Exception as e:
            logger.error(f"Error routing query: {e}")
            # Default routing
            return [AgentType.RESEARCH], ProcessingPriority.MEDIUM
    
    def get_status(self) -> Dict[str, Any]:
        """Get agent status."""
        return {
            "agent": "decision",
            "status": "active",
            "llm_provider": settings.llm_provider,
            "tools_available": [tool.name for tool in self.tools],
            "research_agent_available": self.research_agent is not None,
            "code_agent_available": self.code_agent is not None,
            "routing_rules": {
                k.value: [a.value for a in v] 
                for k, v in self.routing_rules.items()
            },
        }
