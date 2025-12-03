"""
LangGraph workflow implementation for advanced agent coordination.
Provides stateful multi-agent workflows with conditional routing.
"""

from typing import Dict, Any, List, Optional, TypedDict, Annotated, Sequence
from datetime import datetime
import operator
import json

from langgraph.graph import Graph, StateGraph, END
from langgraph.prebuilt import ToolExecutor, ToolInvocation
from langgraph.checkpoint import MemorySaver
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, SystemMessage
from langchain.schema import AgentAction, AgentFinish
from pydantic import BaseModel, Field

from config import settings, logger
from models import (
    QueryType,
    QueryStatus,
    AgentType,
    ProcessingPriority,
    AgentContext,
    AgentResponse,
)
from agents import ResearchAgent, CodeAgent, DecisionAgent


# Define the state for the workflow
class WorkflowState(TypedDict):
    """State definition for the multi-agent workflow."""
    # Input
    query_id: str
    original_query: str
    query_type: QueryType
    priority: ProcessingPriority
    
    # Agent routing
    next_agent: Optional[str]
    agents_visited: List[str]
    
    # Messages and responses
    messages: Annotated[Sequence[BaseMessage], operator.add]
    agent_responses: Dict[str, AgentResponse]
    
    # Processing state
    current_status: QueryStatus
    confidence_scores: Dict[str, float]
    processing_times: Dict[str, float]
    
    # Final output
    final_response: Optional[str]
    total_confidence: float
    metadata: Dict[str, Any]
    
    # Error tracking
    errors: List[str]
    retry_count: int


class OrchestrationWorkflow:
    """
    Advanced orchestration workflow using LangGraph.
    Manages stateful multi-agent coordination with conditional routing.
    """
    
    def __init__(self):
        """Initialize the orchestration workflow."""
        self.research_agent = ResearchAgent()
        self.code_agent = CodeAgent()
        self.decision_agent = DecisionAgent(
            research_agent=self.research_agent,
            code_agent=self.code_agent
        )
        
        # Initialize the workflow graph
        self.workflow = self._build_workflow()
        
        # Memory for conversation continuity
        self.memory = MemorySaver()
        
        # Compile the workflow
        self.app = self.workflow.compile(checkpointer=self.memory)
    
    def _build_workflow(self) -> StateGraph:
        """
        Build the LangGraph workflow with conditional routing.
        
        Returns:
            Configured workflow graph
        """
        # Create the graph
        workflow = StateGraph(WorkflowState)
        
        # Add nodes for each agent
        workflow.add_node("router", self.route_query)
        workflow.add_node("research", self.research_node)
        workflow.add_node("code", self.code_node)
        workflow.add_node("decision", self.decision_node)
        workflow.add_node("synthesizer", self.synthesize_responses)
        workflow.add_node("validator", self.validate_response)
        
        # Set the entry point
        workflow.set_entry_point("router")
        
        # Add conditional edges based on routing logic
        workflow.add_conditional_edges(
            "router",
            self.determine_next_agent,
            {
                "research": "research",
                "code": "code",
                "decision": "decision",
                "synthesizer": "synthesizer",
            }
        )
        
        # Add edges from agents to next steps
        workflow.add_conditional_edges(
            "research",
            self.after_research,
            {
                "code": "code",
                "decision": "decision",
                "synthesizer": "synthesizer",
                "validator": "validator",
            }
        )
        
        workflow.add_conditional_edges(
            "code",
            self.after_code,
            {
                "research": "research",
                "decision": "decision",
                "synthesizer": "synthesizer",
                "validator": "validator",
            }
        )
        
        workflow.add_conditional_edges(
            "decision",
            self.after_decision,
            {
                "research": "research",
                "code": "code",
                "synthesizer": "synthesizer",
                "validator": "validator",
            }
        )
        
        # Synthesizer always goes to validator
        workflow.add_edge("synthesizer", "validator")
        
        # Validator can retry or end
        workflow.add_conditional_edges(
            "validator",
            self.should_retry,
            {
                "router": "router",
                "end": END,
            }
        )
        
        return workflow
    
    async def route_query(self, state: WorkflowState) -> WorkflowState:
        """
        Initial routing node that determines the first agent.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with routing decision
        """
        logger.info(f"Routing query: {state['query_id']}")
        
        # Use decision agent to classify and route
        agents, priority = await self.decision_agent.route_query(
            state["original_query"],
            state["query_type"]
        )
        
        # Update state
        state["priority"] = priority
        state["current_status"] = QueryStatus.PROCESSING
        
        # Determine first agent
        if agents:
            state["next_agent"] = agents[0].value.lower()
        else:
            state["next_agent"] = "research"  # Default
        
        # Add system message
        state["messages"].append(
            SystemMessage(content=f"Query routed to {state['next_agent']} agent with {priority.value} priority")
        )
        
        return state
    
    async def research_node(self, state: WorkflowState) -> WorkflowState:
        """
        Research agent node.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with research results
        """
        logger.info(f"Research agent processing: {state['query_id']}")
        
        try:
            # Create agent context
            context = AgentContext(
                query_id=state["query_id"],
                original_query=state["original_query"],
                query_type=state["query_type"],
                metadata=state.get("metadata", {})
            )
            
            # Process with research agent
            start_time = datetime.utcnow()
            response = await self.research_agent.process(context)
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Update state
            state["agent_responses"]["research"] = response
            state["confidence_scores"]["research"] = response.confidence
            state["processing_times"]["research"] = processing_time
            state["agents_visited"].append("research")
            
            # Add message
            state["messages"].append(
                AIMessage(content=f"Research completed: {response.content[:200]}...")
            )
            
        except Exception as e:
            logger.error(f"Research agent error: {e}")
            state["errors"].append(f"Research agent: {str(e)}")
            state["agent_responses"]["research"] = AgentResponse(
                agent_type=AgentType.RESEARCH,
                content="Research failed",
                confidence=0.0,
                processing_time=0.0,
                error=str(e)
            )
        
        return state
    
    async def code_node(self, state: WorkflowState) -> WorkflowState:
        """
        Code agent node.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with code results
        """
        logger.info(f"Code agent processing: {state['query_id']}")
        
        try:
            # Create agent context
            context = AgentContext(
                query_id=state["query_id"],
                original_query=state["original_query"],
                query_type=state["query_type"],
                metadata=state.get("metadata", {})
            )
            
            # Process with code agent
            start_time = datetime.utcnow()
            response = await self.code_agent.process(context)
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Update state
            state["agent_responses"]["code"] = response
            state["confidence_scores"]["code"] = response.confidence
            state["processing_times"]["code"] = processing_time
            state["agents_visited"].append("code")
            
            # Add message
            state["messages"].append(
                AIMessage(content=f"Code generation completed: {response.content[:200]}...")
            )
            
        except Exception as e:
            logger.error(f"Code agent error: {e}")
            state["errors"].append(f"Code agent: {str(e)}")
            state["agent_responses"]["code"] = AgentResponse(
                agent_type=AgentType.CODE,
                content="Code generation failed",
                confidence=0.0,
                processing_time=0.0,
                error=str(e)
            )
        
        return state
    
    async def decision_node(self, state: WorkflowState) -> WorkflowState:
        """
        Decision agent node for complex decision making.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with decision results
        """
        logger.info(f"Decision agent processing: {state['query_id']}")
        
        try:
            # Create agent context with previous responses
            context = AgentContext(
                query_id=state["query_id"],
                original_query=state["original_query"],
                query_type=state["query_type"],
                processed_data={
                    "previous_responses": state["agent_responses"]
                },
                metadata=state.get("metadata", {})
            )
            
            # Process with decision agent
            start_time = datetime.utcnow()
            response = await self.decision_agent.process(context)
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Update state
            state["agent_responses"]["decision"] = response
            state["confidence_scores"]["decision"] = response.confidence
            state["processing_times"]["decision"] = processing_time
            state["agents_visited"].append("decision")
            
            # Add message
            state["messages"].append(
                AIMessage(content=f"Decision made: {response.content[:200]}...")
            )
            
        except Exception as e:
            logger.error(f"Decision agent error: {e}")
            state["errors"].append(f"Decision agent: {str(e)}")
            state["agent_responses"]["decision"] = AgentResponse(
                agent_type=AgentType.DECISION,
                content="Decision failed",
                confidence=0.0,
                processing_time=0.0,
                error=str(e)
            )
        
        return state
    
    async def synthesize_responses(self, state: WorkflowState) -> WorkflowState:
        """
        Synthesize all agent responses into a final response.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with synthesized response
        """
        logger.info(f"Synthesizing responses for: {state['query_id']}")
        
        try:
            # Collect all responses
            responses = state["agent_responses"]
            
            if not responses:
                state["final_response"] = "No agent responses available"
                state["total_confidence"] = 0.0
                return state
            
            # If only one response, use it directly
            if len(responses) == 1:
                response = list(responses.values())[0]
                state["final_response"] = response.content
                state["total_confidence"] = response.confidence
                return state
            
            # Multiple responses - synthesize them
            synthesis_prompt = f"""
            Synthesize the following agent responses into a comprehensive answer:
            
            Original Query: {state['original_query']}
            
            Agent Responses:
            {json.dumps({k: v.content for k, v in responses.items()}, indent=2)}
            
            Create a unified response that:
            1. Combines key information from all agents
            2. Maintains coherence and accuracy
            3. Preserves important details
            4. Provides clear structure
            """
            
            # Use decision agent's LLM for synthesis
            synthesized = await self.decision_agent.llm.ainvoke(synthesis_prompt)
            
            # Calculate weighted confidence
            total_confidence = 0.0
            total_weight = 0.0
            
            for agent_name, response in responses.items():
                weight = 1.0 if response.error is None else 0.5
                total_confidence += response.confidence * weight
                total_weight += weight
            
            if total_weight > 0:
                state["total_confidence"] = total_confidence / total_weight
            else:
                state["total_confidence"] = 0.5
            
            state["final_response"] = synthesized.content
            
            # Add synthesis message
            state["messages"].append(
                SystemMessage(content=f"Synthesis completed with confidence: {state['total_confidence']:.2f}")
            )
            
        except Exception as e:
            logger.error(f"Synthesis error: {e}")
            state["errors"].append(f"Synthesis: {str(e)}")
            state["final_response"] = "Failed to synthesize responses"
            state["total_confidence"] = 0.0
        
        return state
    
    async def validate_response(self, state: WorkflowState) -> WorkflowState:
        """
        Validate the final response quality.
        
        Args:
            state: Current workflow state
            
        Returns:
            Updated state with validation results
        """
        logger.info(f"Validating response for: {state['query_id']}")
        
        # Validation criteria
        is_valid = True
        validation_issues = []
        
        # Check if response exists
        if not state.get("final_response"):
            is_valid = False
            validation_issues.append("No final response generated")
        
        # Check confidence threshold
        if state.get("total_confidence", 0) < 0.3:
            is_valid = False
            validation_issues.append(f"Low confidence: {state.get('total_confidence', 0):.2f}")
        
        # Check for errors
        if len(state.get("errors", [])) > 2:
            is_valid = False
            validation_issues.append(f"Too many errors: {len(state['errors'])}")
        
        # Check response length
        if state.get("final_response") and len(state["final_response"]) < 50:
            is_valid = False
            validation_issues.append("Response too short")
        
        # Update state
        state["metadata"]["validation"] = {
            "is_valid": is_valid,
            "issues": validation_issues,
            "timestamp": datetime.utcnow().isoformat()
        }
        
        if not is_valid and state.get("retry_count", 0) < settings.retry_max_attempts:
            state["retry_count"] = state.get("retry_count", 0) + 1
            state["current_status"] = QueryStatus.PROCESSING
            logger.warning(f"Validation failed, retry {state['retry_count']}: {validation_issues}")
        else:
            state["current_status"] = QueryStatus.COMPLETED if is_valid else QueryStatus.FAILED
        
        return state
    
    def determine_next_agent(self, state: WorkflowState) -> str:
        """
        Determine which agent to route to based on state.
        
        Args:
            state: Current workflow state
            
        Returns:
            Next node name
        """
        query_type = state["query_type"]
        
        # Route based on query type
        if query_type == QueryType.RESEARCH:
            return "research"
        elif query_type == QueryType.CODE:
            return "code"
        elif query_type == QueryType.DECISION:
            return "decision"
        else:
            # For general queries, start with research
            return "research"
    
    def after_research(self, state: WorkflowState) -> str:
        """
        Determine next step after research agent.
        
        Args:
            state: Current workflow state
            
        Returns:
            Next node name
        """
        query_type = state["query_type"]
        
        # If code-related query and haven't visited code agent
        if "code" not in state["agents_visited"] and (
            query_type == QueryType.CODE or 
            "code" in state["original_query"].lower()
        ):
            return "code"
        
        # If decision needed and haven't visited decision agent
        if "decision" not in state["agents_visited"] and (
            query_type == QueryType.DECISION
        ):
            return "decision"
        
        # Otherwise, go to synthesis
        return "synthesizer"
    
    def after_code(self, state: WorkflowState) -> str:
        """
        Determine next step after code agent.
        
        Args:
            state: Current workflow state
            
        Returns:
            Next node name
        """
        # If research not done and might be helpful
        if "research" not in state["agents_visited"] and (
            state["query_type"] == QueryType.DECISION or
            state.get("confidence_scores", {}).get("code", 0) < 0.7
        ):
            return "research"
        
        # Go to synthesis
        return "synthesizer"
    
    def after_decision(self, state: WorkflowState) -> str:
        """
        Determine next step after decision agent.
        
        Args:
            state: Current workflow state
            
        Returns:
            Next node name
        """
        # Decision agent usually provides final answer
        return "synthesizer"
    
    def should_retry(self, state: WorkflowState) -> str:
        """
        Determine if workflow should retry or end.
        
        Args:
            state: Current workflow state
            
        Returns:
            "router" to retry or "end" to finish
        """
        # Check validation results
        validation = state.get("metadata", {}).get("validation", {})
        
        if not validation.get("is_valid") and state.get("retry_count", 0) < settings.retry_max_attempts:
            return "router"
        
        return "end"
    
    async def execute(
        self,
        query_id: str,
        query: str,
        query_type: QueryType,
        metadata: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Execute the workflow for a query.
        
        Args:
            query_id: Unique query identifier
            query: User query text
            query_type: Type of query
            metadata: Additional metadata
            
        Returns:
            Workflow execution results
        """
        # Initialize state
        initial_state = WorkflowState(
            query_id=query_id,
            original_query=query,
            query_type=query_type,
            priority=ProcessingPriority.MEDIUM,
            next_agent=None,
            agents_visited=[],
            messages=[HumanMessage(content=query)],
            agent_responses={},
            current_status=QueryStatus.PENDING,
            confidence_scores={},
            processing_times={},
            final_response=None,
            total_confidence=0.0,
            metadata=metadata or {},
            errors=[],
            retry_count=0
        )
        
        try:
            # Execute the workflow
            logger.info(f"Starting workflow execution for query: {query_id}")
            
            # Run with thread_id for conversation continuity
            config = {"configurable": {"thread_id": query_id}}
            
            # Stream execution for real-time updates
            async for state in self.app.astream(initial_state, config):
                # Log state updates
                if "messages" in state:
                    last_message = state["messages"][-1] if state["messages"] else None
                    if last_message:
                        logger.debug(f"State update: {last_message.content[:100]}...")
            
            # Get final state
            final_state = state
            
            logger.info(f"Workflow completed for query: {query_id}")
            
            # Return results
            return {
                "query_id": query_id,
                "status": final_state.get("current_status", QueryStatus.FAILED),
                "response": final_state.get("final_response", "No response generated"),
                "confidence": final_state.get("total_confidence", 0.0),
                "agents_used": final_state.get("agents_visited", []),
                "processing_times": final_state.get("processing_times", {}),
                "errors": final_state.get("errors", []),
                "metadata": final_state.get("metadata", {}),
            }
            
        except Exception as e:
            logger.error(f"Workflow execution error: {e}")
            return {
                "query_id": query_id,
                "status": QueryStatus.FAILED,
                "response": f"Workflow execution failed: {str(e)}",
                "confidence": 0.0,
                "agents_used": [],
                "processing_times": {},
                "errors": [str(e)],
                "metadata": {"error_type": type(e).__name__},
            }


# Communication Protocol Implementation
class AgentCommunicationProtocol:
    """
    Protocol for inter-agent communication and message passing.
    """
    
    def __init__(self):
        """Initialize communication protocol."""
        self.message_queue = {}
        self.agent_registry = {}
    
    def register_agent(self, agent_name: str, agent_instance: Any):
        """
        Register an agent in the communication system.
        
        Args:
            agent_name: Name of the agent
            agent_instance: Agent instance
        """
        self.agent_registry[agent_name] = agent_instance
        self.message_queue[agent_name] = []
        logger.info(f"Agent registered: {agent_name}")
    
    async def send_message(
        self,
        from_agent: str,
        to_agent: str,
        message_type: str,
        content: Any
    ):
        """
        Send a message from one agent to another.
        
        Args:
            from_agent: Sending agent name
            to_agent: Receiving agent name
            message_type: Type of message
            content: Message content
        """
        message = {
            "from": from_agent,
            "to": to_agent,
            "type": message_type,
            "content": content,
            "timestamp": datetime.utcnow().isoformat()
        }
        
        if to_agent in self.message_queue:
            self.message_queue[to_agent].append(message)
            logger.debug(f"Message sent from {from_agent} to {to_agent}")
        else:
            logger.warning(f"Agent {to_agent} not registered")
    
    async def receive_messages(self, agent_name: str) -> List[Dict]:
        """
        Receive messages for an agent.
        
        Args:
            agent_name: Agent name
            
        Returns:
            List of messages
        """
        if agent_name in self.message_queue:
            messages = self.message_queue[agent_name]
            self.message_queue[agent_name] = []  # Clear queue
            return messages
        return []
    
    async def broadcast_message(
        self,
        from_agent: str,
        message_type: str,
        content: Any
    ):
        """
        Broadcast a message to all agents.
        
        Args:
            from_agent: Sending agent name
            message_type: Type of message
            content: Message content
        """
        for agent_name in self.agent_registry:
            if agent_name != from_agent:
                await self.send_message(from_agent, agent_name, message_type, content)
    
    def get_agent_status(self, agent_name: str) -> Dict[str, Any]:
        """
        Get status of a specific agent.
        
        Args:
            agent_name: Agent name
            
        Returns:
            Agent status
        """
        if agent_name in self.agent_registry:
            agent = self.agent_registry[agent_name]
            if hasattr(agent, 'get_status'):
                return agent.get_status()
        return {"status": "unknown", "agent": agent_name}
    
    def get_all_agents_status(self) -> Dict[str, Any]:
        """
        Get status of all registered agents.
        
        Returns:
            Dictionary of agent statuses
        """
        statuses = {}
        for agent_name in self.agent_registry:
            statuses[agent_name] = self.get_agent_status(agent_name)
        return statuses


# Export workflow components
__all__ = [
    "OrchestrationWorkflow",
    "AgentCommunicationProtocol",
    "WorkflowState",
]
