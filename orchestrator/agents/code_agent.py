"""
Code Agent for generating, analyzing, and debugging code.
Handles code generation, optimization, and technical solutions.
"""

import asyncio
from typing import Dict, Any, List, Optional
from datetime import datetime
import ast
import black
import json
import subprocess
import tempfile
import os

from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain.tools import Tool, StructuredTool
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.schema import Document
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings, logger
from models import AgentContext, AgentResponse, AgentType


class CodeAgent:
    """
    Code Agent that generates, analyzes, and debugs code.
    Capabilities:
    - Code generation in multiple languages
    - Code analysis and review
    - Bug detection and fixing
    - Performance optimization suggestions
    - Documentation generation
    """
    
    def __init__(self, mongodb_client=None):
        """
        Initialize the Code Agent.
        
        Args:
            mongodb_client: MongoDB client for storing code snippets
        """
        self.mongodb_client = mongodb_client
        self.llm = self._initialize_llm()
        self.tools = self._initialize_tools()
        self.agent = self._create_agent()
        
        # Supported languages
        self.supported_languages = [
            "python", "javascript", "java", "typescript", 
            "go", "rust", "c++", "c#", "ruby", "php"
        ]
    
    def _initialize_llm(self) -> ChatOpenAI:
        """Initialize the language model based on settings."""
        llm_config = settings.get_llm_config()
        
        if settings.llm_provider == "openai":
            return ChatOpenAI(
                model=llm_config.get("model", "gpt-4-turbo-preview"),
                temperature=0.3,  # Lower temperature for code generation
                max_tokens=llm_config.get("max_tokens", 3000),
                openai_api_key=llm_config.get("api_key"),
                timeout=llm_config.get("timeout", 60),
            )
        else:
            raise NotImplementedError(f"Provider {settings.llm_provider} not implemented yet")
    
    def _initialize_tools(self) -> List[Tool]:
        """Initialize code-related tools."""
        tools = []
        
        # Code generation tool
        tools.append(
            Tool(
                name="generate_code",
                description="Generate code based on requirements",
                func=self._generate_code,
            )
        )
        
        # Code analysis tool
        tools.append(
            Tool(
                name="analyze_code",
                description="Analyze code for bugs, performance issues, and improvements",
                func=self._analyze_code,
            )
        )
        
        # Code formatting tool
        tools.append(
            Tool(
                name="format_code",
                description="Format and beautify code",
                func=self._format_code,
            )
        )
        
        # Documentation generation tool
        tools.append(
            Tool(
                name="generate_docs",
                description="Generate documentation for code",
                func=self._generate_documentation,
            )
        )
        
        # Code execution tool (if enabled)
        if settings.code_execution_enabled:
            tools.append(
                Tool(
                    name="execute_code",
                    description="Execute Python code in a safe environment",
                    func=self._execute_code,
                )
            )
        
        # SQL query generator
        tools.append(
            Tool(
                name="generate_sql",
                description="Generate SQL queries based on requirements",
                func=self._generate_sql,
            )
        )
        
        return tools
    
    def _create_agent(self) -> AgentExecutor:
        """Create the LangChain agent."""
        # Define the prompt template
        prompt = ChatPromptTemplate.from_messages([
            ("system", """You are a code agent specializing in software development.
            You are an expert programmer with deep knowledge of multiple programming languages,
            design patterns, algorithms, and best practices.
            
            Guidelines:
            1. Write clean, efficient, and well-documented code
            2. Follow language-specific best practices and conventions
            3. Consider edge cases and error handling
            4. Optimize for readability and maintainability
            5. Include comments for complex logic
            6. Suggest tests when appropriate
            
            When generating code:
            - Start with understanding the requirements
            - Choose appropriate data structures and algorithms
            - Write modular and reusable code
            - Include error handling
            - Provide usage examples
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
    
    async def _generate_code(self, requirements: str) -> str:
        """
        Generate code based on requirements.
        
        Args:
            requirements: Code requirements and specifications
            
        Returns:
            Generated code
        """
        try:
            prompt = f"""
            Generate code for the following requirements:
            {requirements}
            
            Provide:
            1. Complete, working code
            2. Comments explaining key parts
            3. Example usage
            4. Any necessary imports or dependencies
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error generating code: {e}")
            return f"Error generating code: {str(e)}"
    
    async def _analyze_code(self, code: str) -> str:
        """
        Analyze code for issues and improvements.
        
        Args:
            code: Code to analyze
            
        Returns:
            Analysis results
        """
        try:
            # Check if it's Python code for syntax validation
            is_python = "def " in code or "import " in code or "class " in code
            
            analysis = []
            
            # Syntax check for Python
            if is_python:
                try:
                    ast.parse(code)
                    analysis.append("✓ Syntax is valid")
                except SyntaxError as e:
                    analysis.append(f"✗ Syntax error: {e}")
            
            # Ask LLM for detailed analysis
            prompt = f"""
            Analyze the following code for:
            1. Bugs and potential issues
            2. Performance optimizations
            3. Security vulnerabilities
            4. Code quality and best practices
            5. Suggested improvements
            
            Code:
            {code[:settings.code_max_lines * 80]}  # Limit to max lines
            
            Provide specific, actionable feedback.
            """
            
            response = await self.llm.ainvoke(prompt)
            analysis.append(response.content)
            
            return "\n\n".join(analysis)
            
        except Exception as e:
            logger.error(f"Error analyzing code: {e}")
            return f"Error analyzing code: {str(e)}"
    
    def _format_code(self, code: str) -> str:
        """
        Format code (currently supports Python).
        
        Args:
            code: Code to format
            
        Returns:
            Formatted code
        """
        try:
            # Try to format as Python code
            formatted = black.format_str(code, mode=black.Mode())
            return formatted
        except Exception:
            # Return original if formatting fails
            return code
    
    async def _generate_documentation(self, code: str) -> str:
        """
        Generate documentation for code.
        
        Args:
            code: Code to document
            
        Returns:
            Generated documentation
        """
        try:
            prompt = f"""
            Generate comprehensive documentation for the following code:
            
            {code[:settings.code_max_lines * 80]}
            
            Include:
            1. Overview and purpose
            2. Function/class descriptions
            3. Parameters and return values
            4. Usage examples
            5. Any dependencies or requirements
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error generating documentation: {e}")
            return f"Error generating documentation: {str(e)}"
    
    def _execute_code(self, code: str) -> str:
        """
        Execute Python code in a safe environment.
        
        Args:
            code: Python code to execute
            
        Returns:
            Execution results
        """
        if not settings.code_execution_enabled:
            return "Code execution is disabled for security reasons"
        
        try:
            # Create temporary file
            with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as f:
                f.write(code)
                temp_file = f.name
            
            # Execute with timeout
            result = subprocess.run(
                ['python', temp_file],
                capture_output=True,
                text=True,
                timeout=10  # 10 second timeout
            )
            
            # Clean up
            os.unlink(temp_file)
            
            output = []
            if result.stdout:
                output.append(f"Output:\n{result.stdout}")
            if result.stderr:
                output.append(f"Errors:\n{result.stderr}")
            if result.returncode != 0:
                output.append(f"Exit code: {result.returncode}")
            
            return "\n".join(output) if output else "Code executed successfully with no output"
            
        except subprocess.TimeoutExpired:
            os.unlink(temp_file)
            return "Code execution timed out (10 second limit)"
        except Exception as e:
            if 'temp_file' in locals():
                os.unlink(temp_file)
            return f"Error executing code: {str(e)}"
    
    async def _generate_sql(self, requirements: str) -> str:
        """
        Generate SQL queries based on requirements.
        
        Args:
            requirements: SQL query requirements
            
        Returns:
            Generated SQL queries
        """
        try:
            prompt = f"""
            Generate SQL queries for the following requirements:
            {requirements}
            
            Provide:
            1. The SQL query/queries
            2. Explanation of what each query does
            3. Any necessary table structure assumptions
            4. Performance considerations
            """
            
            response = await self.llm.ainvoke(prompt)
            return response.content
            
        except Exception as e:
            logger.error(f"Error generating SQL: {e}")
            return f"Error generating SQL: {str(e)}"
    
    async def process(self, context: AgentContext) -> AgentResponse:
        """
        Process a code-related query.
        
        Args:
            context: Agent context with query information
            
        Returns:
            Agent response with code results
        """
        start_time = datetime.utcnow()
        
        try:
            logger.info(f"Code agent processing query: {context.query_id}")
            
            # Enhance query with code-specific instructions
            enhanced_query = f"""
            Code Request: {context.original_query}
            
            Please provide a complete solution with:
            - Working code
            - Clear comments
            - Error handling
            - Example usage if applicable
            """
            
            # Execute the agent
            result = await self.agent.ainvoke({
                "input": enhanced_query
            })
            
            # Calculate processing time
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            # Extract code blocks from response
            code_blocks = self._extract_code_blocks(result["output"])
            
            # Create response
            response = AgentResponse(
                agent_type=AgentType.CODE,
                content=result["output"],
                confidence=self._calculate_confidence(result, code_blocks),
                processing_time=processing_time,
                metadata={
                    "tools_used": self._get_tools_used(result),
                    "code_blocks": len(code_blocks),
                    "languages": self._detect_languages(code_blocks),
                    "iterations": len(result.get("intermediate_steps", [])),
                }
            )
            
            # Store code snippets in MongoDB if available
            if self.mongodb_client and code_blocks:
                await self._store_code_snippets(context.query_id, code_blocks)
            
            logger.info(f"Code agent completed query: {context.query_id}")
            return response
            
        except Exception as e:
            logger.error(f"Error in code agent: {e}")
            processing_time = (datetime.utcnow() - start_time).total_seconds()
            
            return AgentResponse(
                agent_type=AgentType.CODE,
                content="Failed to generate code solution",
                confidence=0.0,
                processing_time=processing_time,
                error=str(e),
                metadata={"error_type": type(e).__name__}
            )
    
    def _extract_code_blocks(self, text: str) -> List[str]:
        """Extract code blocks from text."""
        code_blocks = []
        
        # Look for markdown code blocks
        import re
        pattern = r'```(?:\w+)?\n(.*?)\n```'
        matches = re.findall(pattern, text, re.DOTALL)
        code_blocks.extend(matches)
        
        # Also look for indented code blocks
        lines = text.split('\n')
        current_block = []
        for line in lines:
            if line.startswith('    '):  # 4 space indent
                current_block.append(line[4:])
            elif current_block:
                if '\n'.join(current_block).strip():
                    code_blocks.append('\n'.join(current_block))
                current_block = []
        
        return code_blocks
    
    def _calculate_confidence(self, result: Dict, code_blocks: List[str]) -> float:
        """Calculate confidence score based on results."""
        # Base confidence
        confidence = 0.5
        
        # Increase confidence if code blocks are present
        if code_blocks:
            confidence += min(0.3, len(code_blocks) * 0.15)
        
        # Increase confidence based on output length
        output = result.get("output", "")
        if len(output) > 500:
            confidence += 0.2
        
        return min(1.0, confidence)
    
    def _detect_languages(self, code_blocks: List[str]) -> List[str]:
        """Detect programming languages in code blocks."""
        languages = []
        
        for code in code_blocks:
            # Simple heuristic-based detection
            if "def " in code or "import " in code:
                languages.append("python")
            elif "function " in code or "const " in code or "let " in code:
                languages.append("javascript")
            elif "public class" in code or "public static" in code:
                languages.append("java")
            elif "SELECT " in code.upper() or "INSERT " in code.upper():
                languages.append("sql")
            elif "fn " in code and "let " in code:
                languages.append("rust")
            elif "func " in code and "package " in code:
                languages.append("go")
        
        return list(set(languages))  # Remove duplicates
    
    def _get_tools_used(self, result: Dict) -> List[str]:
        """Get list of tools used in code generation."""
        tools_used = []
        
        for step in result.get("intermediate_steps", []):
            if len(step) >= 1 and hasattr(step[0], 'tool'):
                tool_name = step[0].tool
                if tool_name not in tools_used:
                    tools_used.append(tool_name)
        
        return tools_used
    
    async def _store_code_snippets(self, query_id: str, code_blocks: List[str]):
        """Store code snippets in MongoDB."""
        try:
            if not self.mongodb_client:
                return
            
            collection = self.mongodb_client.db.code_snippets
            
            for i, code in enumerate(code_blocks):
                document = {
                    "query_id": query_id,
                    "code": code,
                    "index": i,
                    "language": self._detect_languages([code])[0] if self._detect_languages([code]) else "unknown",
                    "created_at": datetime.utcnow()
                }
                await collection.insert_one(document)
            
            logger.debug(f"Stored {len(code_blocks)} code snippets for query {query_id}")
            
        except Exception as e:
            logger.error(f"Error storing code snippets: {e}")
    
    def get_status(self) -> Dict[str, Any]:
        """Get agent status."""
        return {
            "agent": "code",
            "status": "active",
            "llm_provider": settings.llm_provider,
            "tools_available": [tool.name for tool in self.tools],
            "code_execution_enabled": settings.code_execution_enabled,
            "supported_languages": self.supported_languages,
            "max_code_lines": settings.code_max_lines,
        }
