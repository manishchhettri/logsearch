#!/bin/bash

# Long Stack Trace Generator
# Generates logs with extremely long stack traces (100+ lines)
# Usage: ./generate-long-stacktraces.sh [filename]

FILENAME=${1:-long-stacktraces.log}
OUTPUT_DIR="./logs"

# Create logs directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

OUTPUT_FILE="$OUTPUT_DIR/$FILENAME"

echo "Generating long stack trace log file: $OUTPUT_FILE"

# Clear the file if it exists
> "$OUTPUT_FILE"

# Pattern 1: Very deep call stack (80+ lines)
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 15:30:00,123] [[ACTIVE] ExecuteThread: '5' for queue: 'weblogic.kernel.Default (self-tuning)'] [ERROR] [com.example.DeepRecursionHandler] [] [user:admin] - StackOverflowError: Recursive call depth exceeded
   at com.example.validation.DataValidator.validateLevel80(DataValidator.java:1234)
   at com.example.validation.DataValidator.validateLevel79(DataValidator.java:1233)
   at com.example.validation.DataValidator.validateLevel78(DataValidator.java:1232)
   at com.example.validation.DataValidator.validateLevel77(DataValidator.java:1231)
   at com.example.validation.DataValidator.validateLevel76(DataValidator.java:1230)
   at com.example.validation.DataValidator.validateLevel75(DataValidator.java:1229)
   at com.example.validation.DataValidator.validateLevel74(DataValidator.java:1228)
   at com.example.validation.DataValidator.validateLevel73(DataValidator.java:1227)
   at com.example.validation.DataValidator.validateLevel72(DataValidator.java:1226)
   at com.example.validation.DataValidator.validateLevel71(DataValidator.java:1225)
   at com.example.validation.DataValidator.validateLevel70(DataValidator.java:1224)
   at com.example.validation.DataValidator.validateLevel69(DataValidator.java:1223)
   at com.example.validation.DataValidator.validateLevel68(DataValidator.java:1222)
   at com.example.validation.DataValidator.validateLevel67(DataValidator.java:1221)
   at com.example.validation.DataValidator.validateLevel66(DataValidator.java:1220)
   at com.example.validation.DataValidator.validateLevel65(DataValidator.java:1219)
   at com.example.validation.DataValidator.validateLevel64(DataValidator.java:1218)
   at com.example.validation.DataValidator.validateLevel63(DataValidator.java:1217)
   at com.example.validation.DataValidator.validateLevel62(DataValidator.java:1216)
   at com.example.validation.DataValidator.validateLevel61(DataValidator.java:1215)
   at com.example.validation.DataValidator.validateLevel60(DataValidator.java:1214)
   at com.example.validation.DataValidator.validateLevel59(DataValidator.java:1213)
   at com.example.validation.DataValidator.validateLevel58(DataValidator.java:1212)
   at com.example.validation.DataValidator.validateLevel57(DataValidator.java:1211)
   at com.example.validation.DataValidator.validateLevel56(DataValidator.java:1210)
   at com.example.validation.DataValidator.validateLevel55(DataValidator.java:1209)
   at com.example.validation.DataValidator.validateLevel54(DataValidator.java:1208)
   at com.example.validation.DataValidator.validateLevel53(DataValidator.java:1207)
   at com.example.validation.DataValidator.validateLevel52(DataValidator.java:1206)
   at com.example.validation.DataValidator.validateLevel51(DataValidator.java:1205)
   at com.example.validation.DataValidator.validateLevel50(DataValidator.java:1204)
   at com.example.validation.DataValidator.validateLevel49(DataValidator.java:1203)
   at com.example.validation.DataValidator.validateLevel48(DataValidator.java:1202)
   at com.example.validation.DataValidator.validateLevel47(DataValidator.java:1201)
   at com.example.validation.DataValidator.validateLevel46(DataValidator.java:1200)
   at com.example.validation.DataValidator.validateLevel45(DataValidator.java:1199)
   at com.example.validation.DataValidator.validateLevel44(DataValidator.java:1198)
   at com.example.validation.DataValidator.validateLevel43(DataValidator.java:1197)
   at com.example.validation.DataValidator.validateLevel42(DataValidator.java:1196)
   at com.example.validation.DataValidator.validateLevel41(DataValidator.java:1195)
   at com.example.validation.DataValidator.validateLevel40(DataValidator.java:1194)
   at com.example.validation.DataValidator.validateLevel39(DataValidator.java:1193)
   at com.example.validation.DataValidator.validateLevel38(DataValidator.java:1192)
   at com.example.validation.DataValidator.validateLevel37(DataValidator.java:1191)
   at com.example.validation.DataValidator.validateLevel36(DataValidator.java:1190)
   at com.example.validation.DataValidator.validateLevel35(DataValidator.java:1189)
   at com.example.validation.DataValidator.validateLevel34(DataValidator.java:1188)
   at com.example.validation.DataValidator.validateLevel33(DataValidator.java:1187)
   at com.example.validation.DataValidator.validateLevel32(DataValidator.java:1186)
   at com.example.validation.DataValidator.validateLevel31(DataValidator.java:1185)
   at com.example.validation.DataValidator.validateLevel30(DataValidator.java:1184)
   at com.example.validation.DataValidator.validateLevel29(DataValidator.java:1183)
   at com.example.validation.DataValidator.validateLevel28(DataValidator.java:1182)
   at com.example.validation.DataValidator.validateLevel27(DataValidator.java:1181)
   at com.example.validation.DataValidator.validateLevel26(DataValidator.java:1180)
   at com.example.validation.DataValidator.validateLevel25(DataValidator.java:1179)
   at com.example.validation.DataValidator.validateLevel24(DataValidator.java:1178)
   at com.example.validation.DataValidator.validateLevel23(DataValidator.java:1177)
   at com.example.validation.DataValidator.validateLevel22(DataValidator.java:1176)
   at com.example.validation.DataValidator.validateLevel21(DataValidator.java:1175)
   at com.example.validation.DataValidator.validateLevel20(DataValidator.java:1174)
   at com.example.validation.DataValidator.validateLevel19(DataValidator.java:1173)
   at com.example.validation.DataValidator.validateLevel18(DataValidator.java:1172)
   at com.example.validation.DataValidator.validateLevel17(DataValidator.java:1171)
   at com.example.validation.DataValidator.validateLevel16(DataValidator.java:1170)
   at com.example.validation.DataValidator.validateLevel15(DataValidator.java:1169)
   at com.example.validation.DataValidator.validateLevel14(DataValidator.java:1168)
   at com.example.validation.DataValidator.validateLevel13(DataValidator.java:1167)
   at com.example.validation.DataValidator.validateLevel12(DataValidator.java:1166)
   at com.example.validation.DataValidator.validateLevel11(DataValidator.java:1165)
   at com.example.validation.DataValidator.validateLevel10(DataValidator.java:1164)
   at com.example.validation.DataValidator.validateLevel9(DataValidator.java:1163)
   at com.example.validation.DataValidator.validateLevel8(DataValidator.java:1162)
   at com.example.validation.DataValidator.validateLevel7(DataValidator.java:1161)
   at com.example.validation.DataValidator.validateLevel6(DataValidator.java:1160)
   at com.example.validation.DataValidator.validateLevel5(DataValidator.java:1159)
   at com.example.validation.DataValidator.validateLevel4(DataValidator.java:1158)
   at com.example.validation.DataValidator.validateLevel3(DataValidator.java:1157)
   at com.example.validation.DataValidator.validateLevel2(DataValidator.java:1156)
   at com.example.validation.DataValidator.validateLevel1(DataValidator.java:1155)
   at com.example.validation.DataValidator.validate(DataValidator.java:1154)
   at com.example.controller.ValidationController.handleRequest(ValidationController.java:89)
   at org.springframework.web.method.support.InvocableHandlerMethod.invoke(InvocableHandlerMethod.java:219)
   at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:102)
 caseID=DEEP-STACK-001
 sessionID=SESSION-1234567890
 userID=admin
EOF

# Pattern 2: Multiple nested "Caused by" exceptions (120+ lines)
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
   at oracle.jdbc.driver.T4CPreparedStatement.doOall8(T4CPreparedStatement.java:53)
   at oracle.jdbc.driver.T4CPreparedStatement.executeForRows(T4CPreparedStatement.java:943)
   at oracle.jdbc.driver.OracleStatement.doExecuteWithTimeout(OracleStatement.java:1150)
   at oracle.jdbc.driver.OraclePreparedStatement.executeInternal(OraclePreparedStatement.java:4798)
   at oracle.jdbc.driver.OraclePreparedStatement.executeUpdate(OraclePreparedStatement.java:4875)
   at oracle.jdbc.driver.OraclePreparedStatementWrapper.executeUpdate(OraclePreparedStatementWrapper.java:1361)
   at weblogic.jdbc.wrapper.PreparedStatement.executeUpdate(PreparedStatement.java:133)
   at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:175)
   at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:3267)
   at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:3828)
   at org.hibernate.action.internal.EntityInsertAction.execute(EntityInsertAction.java:107)
   at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:604)
   at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:478)
   at org.hibernate.event.internal.AbstractFlushingEventListener.performExecutions(AbstractFlushingEventListener.java:356)
   at org.hibernate.event.internal.DefaultFlushEventListener.onFlush(DefaultFlushEventListener.java:39)
   at org.hibernate.internal.SessionImpl.flush(SessionImpl.java:1454)
   at org.hibernate.internal.SessionImpl.managedFlush(SessionImpl.java:511)
   at org.hibernate.jpa.spi.AbstractEntityManagerImpl.flush(AbstractEntityManagerImpl.java:1301)
   at curam.persistence.impl.DatabaseManager.executeUpdate(DatabaseManager.java:523)
   ... 14 more
Caused by: oracle.jdbc.OracleDatabaseException: ORA-00001: unique constraint (SCHEMA.UK_ENTITY_123) violated
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:513)
   at oracle.jdbc.driver.T4CTTIoer.processError(T4CTTIoer.java:461)
   at oracle.jdbc.driver.T4C8Oall.processError(T4C8Oall.java:1104)
   ... 40 more
Suppressed: java.lang.IllegalStateException: Transaction already marked for rollback
   at org.hibernate.engine.transaction.internal.TransactionImpl.begin(TransactionImpl.java:67)
   at org.hibernate.internal.SessionImpl.beginTransaction(SessionImpl.java:1587)
   at curam.persistence.impl.TransactionManager.beginTransaction(TransactionManager.java:89)
   at curam.core.impl.EntityService.executeInTransaction(EntityService.java:234)
   at curam.core.impl.EntityService.saveEntity(EntityService.java:139)
   ... 34 more
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

# Pattern 3: Very long framework stack (Spring, Hibernate, WebLogic)
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
   at weblogic.servlet.internal.StubSecurityHelper$ServletServiceAction.run(StubSecurityHelper.java:260)
   at weblogic.servlet.internal.StubSecurityHelper.invokeServlet(StubSecurityHelper.java:137)
   at weblogic.servlet.internal.ServletStubImpl.execute(ServletStubImpl.java:350)
   at weblogic.servlet.internal.TailFilter.doFilter(TailFilter.java:25)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at curam.util.web.filter.CharacterEncodingFilter.doFilter(CharacterEncodingFilter.java:89)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at org.springframework.web.filter.HiddenHttpMethodFilter.doFilterInternal(HiddenHttpMethodFilter.java:94)
   at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201)
   at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:337)
   at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.invoke(FilterSecurityInterceptor.java:115)
   at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.doFilter(FilterSecurityInterceptor.java:81)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:122)
   at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:116)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:126)
   at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:81)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:109)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:149)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:103)
   at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:89)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90)
   at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75)
   at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:112)
   at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:82)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:55)
   at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117)
   at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346)
   at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:221)
   at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:186)
   at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:354)
   at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:267)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at weblogic.servlet.internal.RequestEventsFilter.doFilter(RequestEventsFilter.java:32)
   at weblogic.servlet.internal.FilterChainImpl.doFilter(FilterChainImpl.java:78)
   at weblogic.servlet.internal.WebAppServletContext$ServletInvocationAction.wrapRun(WebAppServletContext.java:3656)
   at weblogic.servlet.internal.WebAppServletContext$ServletInvocationAction.run(WebAppServletContext.java:3622)
   at weblogic.security.acl.internal.AuthenticatedSubject.doAs(AuthenticatedSubject.java:326)
   at weblogic.security.service.SecurityManager.runAsForUserCode(SecurityManager.java:197)
   at weblogic.servlet.provider.WlsSecurityProvider.runAsForUserCode(WlsSecurityProvider.java:203)
   at weblogic.servlet.provider.WlsSubjectHandle.run(WlsSubjectHandle.java:71)
   at weblogic.servlet.internal.WebAppServletContext.doSecuredExecute(WebAppServletContext.java:2433)
   at weblogic.servlet.internal.WebAppServletContext.securedExecute(WebAppServletContext.java:2281)
   at weblogic.servlet.internal.WebAppServletContext.execute(WebAppServletContext.java:2259)
   at weblogic.servlet.internal.ServletRequestImpl.runInternal(ServletRequestImpl.java:1691)
   at weblogic.servlet.internal.ServletRequestImpl.run(ServletRequestImpl.java:1651)
   at weblogic.servlet.provider.ContainerSupportProviderImpl$WlsRequestExecutor.run(ContainerSupportProviderImpl.java:270)
   at weblogic.invocation.ComponentInvocationContextManager._runAs(ComponentInvocationContextManager.java:348)
   at weblogic.invocation.ComponentInvocationContextManager.runAs(ComponentInvocationContextManager.java:333)
   at weblogic.work.LivePartitionUtility.doRunWorkUnderContext(LivePartitionUtility.java:54)
   at weblogic.work.PartitionUtility.runWorkUnderContext(PartitionUtility.java:41)
   at weblogic.work.SelfTuningWorkManagerImpl.runWorkUnderContext(SelfTuningWorkManagerImpl.java:640)
   at weblogic.work.ExecuteThread.execute(ExecuteThread.java:406)
   at weblogic.work.ExecuteThread.run(ExecuteThread.java:346)
Caused by: java.lang.OutOfMemoryError: Java heap space
   at java.util.Arrays.copyOf(Arrays.java:3332)
   at java.lang.AbstractStringBuilder.ensureCapacityInternal(AbstractStringBuilder.java:124)
   at java.lang.AbstractStringBuilder.append(AbstractStringBuilder.java:448)
   at java.lang.StringBuilder.append(StringBuilder.java:136)
   at curam.util.xml.XMLBuilder.buildDocument(XMLBuilder.java:234)
   at curam.core.impl.ReportGenerator.generateLargeReport(ReportGenerator.java:456)
   at curam.core.impl.ReportService.createReport(ReportService.java:189)
   at curam.core.facade.ReportFacade.executeReport(ReportFacade.java:112)
   at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   at java.lang.reflect.Method.invoke(Method.java:498)
   at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:344)
   at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:198)
   at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
   at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:367)
   at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:118)
   at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)
   at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:212)
   at com.sun.proxy.$Proxy234.executeReport(Unknown Source)
   at curam.web.controller.ReportController.handleReportRequest(ReportController.java:78)
   at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   at java.lang.reflect.Method.invoke(Method.java:498)
   at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)
   at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150)
   at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)
   at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)
   at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808)
   at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
   at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1071)
   ... 75 more
 requestID=REQ-LONG-STACK-001
 heapSize=4096MB
 usedHeap=4095MB
 availableHeap=1MB
EOF

# Add a normal log entry after to test boundary detection
cat >> "$OUTPUT_FILE" << 'EOF'
[13 Mar 2026 15:45:00,001] [[ACTIVE] ExecuteThread: '1' for queue: 'weblogic.kernel.Default (self-tuning)'] [INFO ] [curam.core.impl.SystemMonitor] [] [user:system] - Application recovered from error state
EOF

echo "✓ Generated $OUTPUT_FILE with extremely long stack traces"
echo ""
echo "Stack trace patterns included:"
echo "  1. Deep recursive call stack (80+ levels)"
echo "  2. Multiple nested exceptions with 'Caused by' and 'Suppressed' (120+ lines total)"
echo "  3. Long framework stack trace (100+ lines with Spring/Hibernate/WebLogic)"
echo ""
echo "Total lines: $(wc -l < "$OUTPUT_FILE")"
echo ""
echo "Test searches:"
echo "  - Search for: DEEP-STACK-001"
echo "  - Search for: WF-NESTED-EXCEPTION-001"
echo "  - Search for: REQ-LONG-STACK-001"
echo "  - Search for: OutOfMemoryError"
