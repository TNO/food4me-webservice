<html>
	<head>
		<meta name="layout" content="main" />
		<title>Food4me Administration panel</title>
	</head>
	<body>
		<h1>Administration tasks</h1>
		<div id="adminPanel">
			<p>The database contains:</p>
			<ul>
				<li>${numProperties} properties and ${numUnits} units</li>
				<li>${numReferences} reference values for ${numReferenceSubjects} properties</li>
				<li>${numAdvices} advices for ${numAdviceSubjects} properties</li>
				<li>
					<g:if test="${languages}">
						(partial) translations in ${languages?.join(", ")}
					</g:if>
					<g:else>
						no translations
					</g:else>
				</li>
				<li><g:link action="consistencyCheck">Consistency check</g:link></li>
			</ul>
			
			<p>Import directory</p>
			<ul>
				<li>${importDirectory}</li>
			</ul>
						
			<p>Application</p>
			<ul>
				<li>${appName}</li>
				<li>${appVersion}</li>
			</ul>			
			
		</div>

		<div class="admin">
			<p>
				This page allows the administrator to perform certain tasks. Please use with care!
			</p>

			<g:if test="${flash.logs}">
				<h3>Import logs: ${flash.logTitle}</h3>
				<ul class="log_messages">
					<g:each var="level" in="${flash.logs}">
						<g:if test="${level.key in [ 'info', 'warn', 'error' ]}">
							<li>
								<h4>${level.key}</h4>

								<ul>
									<g:each var="message" in="${level.value}">
										<li>${message}</li>
									</g:each>
								</ul>
							</li>
						</g:if>
					</g:each>
				</ul>
			</g:if>

			<h3>Load data</h3>
			<table>
				<thead>
					<tr><th>Data on server</th><th>Custom data</th></tr>
				</thead>
				<tbody>
					<tr>
						<td>
							<g:form action="importAll">
								<input type="submit" value="Import all data" />
							</g:form>

							<g:form action="importReferenceData">
								<input type="submit" value="Import reference data only" />
							</g:form>

							<g:form action="importDecisionTrees">
								<input type="submit" value="Import decision trees" />
							</g:form>

							<g:form action="importTranslations">
								<input type="submit" value="Import advice translations" />
							</g:form>
						</td>
						<td>
							<g:uploadForm action="importAll">
								<input type="file" name="zipfile" />
								<input type="submit" value="Import custom data" />
							</g:uploadForm>
						</td>
					</tr>
				</tbody>
			</table>

			<hr />
			<h3>Load example data</h3>
			<g:form action="importExampleData">
				<input type="submit" value="Load example data" />
			</g:form>

			<hr />
			<h3>Clear database</h3>

			<g:form action="clearAll">
				<input type="submit" value="Clear the database" />
			</g:form>

		</div>
	</body>
</html>
