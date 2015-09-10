<g:if test="${logs}">
	<h3>${title}</h3>
	<ul class="log_messages">
		<g:each var="level" in="${levels}">
			<g:if test="${logs.containsKey(level)}">
				<li>
					<h4>${level}</h4>

					<ul>
						<g:each var="message" in="${logs[level]}">
							<li>${message}</li>
						</g:each>
					</ul>
				</li>
			</g:if>
		</g:each>
	</ul>
</g:if>
