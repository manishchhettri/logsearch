#!/bin/bash

# Long Stack Trace Generator
# Generates logs with EXTREMELY long stack traces (300-1000+ lines)
# Usage: ./generate-long-stacktraces.sh [filename]

FILENAME=${1:-long-stacktraces.log}
OUTPUT_DIR="./logs"

# Create logs directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

OUTPUT_FILE="$OUTPUT_DIR/$FILENAME"

echo "Generating extremely long stack trace log file: $OUTPUT_FILE"

# Clear the file if it exists
> "$OUTPUT_FILE"

# Pattern 1: VERY deep recursive call stack (300+ levels)
echo "[13 Mar 2026 15:30:00,123] [[ACTIVE] ExecuteThread: '5' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [com.example.DeepRecursionHandler] [] [user:admin] - StackOverflowError: Recursive call depth exceeded" >> "$OUTPUT_FILE"

# Generate 300 recursive levels
for i in {300..1}; do
    echo "   at com.example.validation.DataValidator.validateLevel$i(DataValidator.java:$((1000 + i)))" >> "$OUTPUT_FILE"
done

# Add base frames
cat >> "$OUTPUT_FILE" << 'EOF'
   at com.example.validation.DataValidator.validate(DataValidator.java:1000)
   at com.example.controller.ValidationController.handleRequest(ValidationController.java:89)
   at org.springframework.web.method.support.InvocableHandlerMethod.invoke(InvocableHandlerMethod.java:219)
   at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:102)
   at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)
   at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1071)
 caseID=DEEP-STACK-001
 sessionID=SESSION-1234567890
 userID=admin
EOF

# Pattern 2: Multiple nested "Caused by" exceptions with MANY suppressed exceptions (500+ lines)
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 15:35:00,456] [[STANDBY] ExecuteThread: '12' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [curam.workflow.impl.WorkflowEngine] [] [user:system] - Failed to process workflow: Multiple nested exceptions detected
   at curam.workflow.impl.WorkflowEngine.executeWorkflow(WorkflowEngine.java:456)
   at curam.workflow.impl.WorkflowManager.processWorkflowStep(WorkflowManager.java:234)
   at curam.workflow.impl.WorkflowManager.handleWorkflowExecution(WorkflowManager.java:189)
   at curam.workflow.facade.WorkflowFacade.execute(WorkflowFacade.java:112)
   at curam.core.sl.impl.WorkflowDelegate.processRequest(WorkflowDelegate.java:78)
   at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1040)
   at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:943)
   at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)
Caused by: curam.util.exception.AppException: Database constraint violation detected
   at curam.persistence.impl.DatabaseManager.executeUpdate(DatabaseManager.java:567)
   at curam.persistence.impl.EntityManagerImpl.persist(EntityManagerImpl.java:234)
   at curam.core.impl.EntityService.saveEntity(EntityService.java:145)
   at curam.core.impl.EntityService.createOrUpdate(EntityService.java:98)
   at curam.workflow.impl.WorkflowEngine.saveWorkflowState(WorkflowEngine.java:334)
   at curam.workflow.impl.WorkflowEngine.executeWorkflow(WorkflowEngine.java:432)
   ... 8 more
Caused by: java.sql.SQLException: ORA-00001: unique constraint (SCHEMA.UK_ENTITY_123) violated
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:450)
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:399)
   at oracle.jdbc.driver.T4C8Oall.processError(T4C8Oall.java:1059)
   at oracle.jdbc.driver.T4CTTIfun.receive(T4CTTIfun.java:522)
   at oracle.jdbc.driver.T4CTTIfun.doRPC(T4CTTIfun.java:257)
   at oracle.jdbc.driver.T4C8Oall.doOALL(T4C8Oall.java:587)
   at oracle.jdbc.driver.T4CPreparedStatement.doOall8(T4CPreparedStatement.java:225)
   at oracle.jdbc.driver.T4CPreparedStatement.executeForRows(T4CPreparedStatement.java:943)
   at oracle.jdbc.driver.OracleStatement.doExecuteWithTimeout(OracleStatement.java:1150)
   at oracle.jdbc.driver.OraclePreparedStatement.executeInternal(OraclePreparedStatement.java:4798)
   at oracle.jdbc.driver.OraclePreparedStatement.executeUpdate(OraclePreparedStatement.java:4875)
   at weblogic.jdbc.wrapper.PreparedStatement.executeUpdate(PreparedStatement.java:133)
   at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:175)
   at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:3267)
   at org.hibernate.action.internal.EntityInsertAction.execute(EntityInsertAction.java:107)
   at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:604)
   at org.hibernate.event.internal.AbstractFlushingEventListener.performExecutions(AbstractFlushingEventListener.java:356)
   at org.hibernate.event.internal.DefaultFlushEventListener.onFlush(DefaultFlushEventListener.java:39)
   at org.hibernate.internal.SessionImpl.flush(SessionImpl.java:1454)
   at curam.persistence.impl.DatabaseManager.executeUpdate(DatabaseManager.java:523)
   ... 14 more
Caused by: oracle.jdbc.OracleDatabaseException: ORA-00001: unique constraint (SCHEMA.UK_ENTITY_123) violated
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:513)
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:461)
   at oracle.jdbc.driver.T4C8Oall.processError(T4C8Oall.java:1104)
   ... 40 more
EOF

# Generate 100 suppressed exceptions to make it really long
for i in {1..100}; do
    cat >> "$OUTPUT_FILE" << EOF
Suppressed: java.lang.IllegalStateException: Transaction already marked for rollback (attempt $i)
   at org.hibernate.engine.transaction.internal.TransactionImpl.begin(TransactionImpl.java:$((60 + i)))
   at org.hibernate.internal.SessionImpl.beginTransaction(SessionImpl.java:$((1580 + i)))
   at curam.persistence.impl.TransactionManager.beginTransaction(TransactionManager.java:$((80 + i)))
   at curam.core.impl.EntityService.executeInTransaction(EntityService.java:$((230 + i)))
   at curam.core.impl.EntityService.saveEntity(EntityService.java:$((130 + i)))
   at curam.retry.RetryHandler.attemptOperation$i(RetryHandler.java:$((100 + i * 10)))
   at curam.retry.RetryHandler.handleFailure(RetryHandler.java:$((50 + i)))
   ... $((30 + i)) more
EOF
done

cat >> "$OUTPUT_FILE" << 'EOF'
Suppressed: javax.persistence.RollbackException: Error while committing the transaction
   at org.hibernate.jpa.internal.TransactionImpl.commit(TransactionImpl.java:101)
   at curam.persistence.impl.TransactionManager.commitTransaction(TransactionManager.java:145)
   at curam.core.impl.EntityService.executeInTransaction(EntityService.java:267)
   ... 36 more
   Caused by: javax.persistence.PersistenceException: org.hibernate.exception.ConstraintViolationException
      at org.hibernate.jpa.spi.AbstractEntityManagerImpl.convert(AbstractEntityManagerImpl.java:1763)
      at org.hibernate.jpa.spi.AbstractEntityManagerImpl.convert(AbstractEntityManagerImpl.java:1677)
      at org.hibernate.jpa.internal.TransactionImpl.commit(TransactionImpl.java:87)
      ... 38 more
   Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement
      at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:72)
      at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:49)
      at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:126)
      at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:112)
      ... 67 more
 workflowID=WF-NESTED-EXCEPTION-001
 errorCode=ORA-00001
 attemptNumber=3
 retryScheduled=false
EOF

# Pattern 3: EXTREMELY long framework stack (Spring, Hibernate, WebLogic) - 1000+ lines
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 15:40:00,789] [[ACTIVE] ExecuteThread: '25' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [org.springframework.web.servlet.DispatcherServlet] [] [user:bob.wilson] - Handler dispatch failed: nested exception is java.lang.OutOfMemoryError: Java heap space
   at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089)
   at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979)
   at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
   at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
   at javax.servlet.http.HttpServlet.service(HttpServlet.java:755)
   at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883)
   at javax.servlet.http.HttpServlet.service(HttpServlet.java:848)
   at weblogic.servlet.internal.StubSecurityHelper$ServletServiceAction.run(StubSecurityHelper.java:286)
   at weblogic.servlet.internal.StubSecurityHelper.invokeServlet(StubSecurityHelper.java:137)
   at weblogic.servlet.internal.ServletStubImpl.execute(ServletStubImpl.java:350)
   at weblogic.servlet.internal.TailFilter.doFilter(TailFilter.java:25)
EOF

# Generate deep filter chain (200 filters)
for i in {1..200}; do
    filter_name="CustomFilter$i"
    echo "   at curam.web.filter.$filter_name.doFilter($filter_name.java:$((40 + i % 50)))" >> "$OUTPUT_FILE"
    echo "   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)" >> "$OUTPUT_FILE"
done

cat >> "$OUTPUT_FILE" << 'EOF'
   at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201)
   at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:337)
   at weblogic.servlet.internal.RequestEventsFilter.doFilter(RequestEventsFilter.java:32)
   at weblogic.servlet.internal.WebAppServletContext$ServletInvocationAction.wrapRun(WebAppServletContext.java:3656)
   at weblogic.servlet.internal.WebAppServletContext.execute(WebAppServletContext.java:2259)
   at weblogic.servlet.internal.ServletRequestImpl.runInternal(ServletRequestImpl.java:1691)
   at weblogic.work.ExecuteThread.execute(ExecuteThread.java:406)
   at weblogic.work.ExecuteThread.run(ExecuteThread.java:346)
Caused by: java.lang.OutOfMemoryError: Java heap space
   at java.util.Arrays.copyOf(Arrays.java:3332)
   at java.lang.AbstractStringBuilder.ensureCapacityInternal(AbstractStringBuilder.java:124)
   at java.lang.AbstractStringBuilder.append(AbstractStringBuilder.java:448)
   at java.lang.StringBuilder.append(StringBuilder.java:136)
EOF

# Generate deep recursion in XML building (300 levels)
for i in {1..300}; do
    echo "   at curam.util.xml.XMLBuilder.buildNode$i(XMLBuilder.java:$((200 + i)))" >> "$OUTPUT_FILE"
    echo "   at curam.util.xml.XMLBuilder.processElement(XMLBuilder.java:$((150 + i % 100)))" >> "$OUTPUT_FILE"
done

cat >> "$OUTPUT_FILE" << 'EOF'
   at curam.util.xml.XMLBuilder.buildDocument(XMLBuilder.java:234)
   at curam.core.impl.ReportGenerator.generateLargeReport(ReportGenerator.java:456)
   at curam.core.impl.ReportService.createReport(ReportService.java:189)
   at curam.core.facade.ReportFacade.executeReport(ReportFacade.java:112)
   at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   at java.lang.reflect.Method.invoke(Method.java:498)
EOF

# Generate deep AOP/Proxy chain (100 levels)
for i in {1..100}; do
    echo "   at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:$((160 + i % 50)))" >> "$OUTPUT_FILE"
    echo "   at org.springframework.aop.interceptor.CustomInterceptor$i.invoke(CustomInterceptor$i.java:$((50 + i)))" >> "$OUTPUT_FILE"
done

cat >> "$OUTPUT_FILE" << 'EOF'
   at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:344)
   at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:118)
   at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:212)
   at com.sun.proxy.$Proxy234.executeReport(Unknown Source)
   at curam.web.controller.ReportController.handleReportRequest(ReportController.java:78)
   at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)
   at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)
   at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)
   at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)
   at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808)
   at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
   at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1071)
   ... 975 more
 requestID=REQ-LONG-STACK-001
 heapSize=4096MB
 usedHeap=4095MB
 availableHeap=1MB
EOF

# Add a normal log entry after to test boundary detection
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 15:45:00,001] [[ACTIVE] ExecuteThread: '1' for queue: 'weblogic.kernel.Default (self-tuning)'] [INFO ] [curam.core.impl.SystemMonitor] [] [user:system] - Application recovered from error state
EOF

echo "✓ Generated $OUTPUT_FILE with EXTREMELY long stack traces"
echo ""
echo "Stack trace patterns included:"
echo "  1. Deep recursive call stack (300+ levels)"
echo "  2. Multiple nested exceptions with 100+ suppressed exceptions (500+ lines total)"
echo "  3. EXTREMELY long framework stack trace (1000+ lines with filters, AOP, recursion)"
echo ""
echo "Total lines: $(wc -l < "$OUTPUT_FILE")"
echo ""
echo "Test searches:"
echo "  - Search for: DEEP-STACK-001"
echo "  - Search for: WF-NESTED-EXCEPTION-001"
echo "  - Search for: REQ-LONG-STACK-001"
echo "  - Search for: OutOfMemoryError"
echo ""
echo "These extreme stack traces will test:"
echo "  - Pattern field truncation (should limit to 1500 chars)"
echo "  - Full message searchability (should remain intact)"
echo "  - Multi-line log entry parsing"
echo "  - Download/extraction of large log entries"
