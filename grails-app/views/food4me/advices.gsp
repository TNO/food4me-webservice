<%@ page import="eu.qualify.food4me.ModifiedProperty" %>
<html>
	<head>
		<meta name="layout" content="main" />
		<title>Food4me advices</title>
	</head>
	<body>
	<h1 style="font-size: 180%; font-weight: 400">Deelnemer: ${userId}</h1>

	<h1 style="font-size: 180%; font-weight: 400">Adviezen</h1>
	<div id="advices">
		<ul>
			<g:each in="${advices}" var="advice">
				<li style="list-style: none">
					<g:lines string="${translations[advice.code]}" />
				</li>
			</g:each>
		</ul>
	</div>


	<g:if test="${flash.logs}">
		<h1 style="font-size: 180%; font-weight: 400">Logs</h1>
		<g:render template="/logs" model="['logs': flash.logs, 'title': flash.logTitle, levels: [ 'error', 'warn', 'info' ] ]" />
	</g:if>
	</body>
</html>
