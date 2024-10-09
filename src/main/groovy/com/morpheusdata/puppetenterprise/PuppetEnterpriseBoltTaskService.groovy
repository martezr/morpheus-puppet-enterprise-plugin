package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.AbstractTaskService
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.model.*
import groovy.util.logging.Slf4j
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions


@Slf4j
class PuppetEnterpriseBoltTaskService extends AbstractTaskService {
	MorpheusContext context

	PuppetEnterpriseBoltTaskService(MorpheusContext context) {
		this.context = context
	}

	@Override
	MorpheusContext getMorpheus() {
		return context
	}

	@Override
	TaskResult executeLocalTask(Task task, Map opts, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildLocalTaskConfig([:], task, [], opts).blockingGet()
		log.info "TASK INSTANCE: ${instance.name}"
		if(instance) {
			config = buildInstanceTaskConfig(instance, [:], task, [], opts).blockingGet()
		}
		if(container) {
			config = buildContainerTaskConfig(container, [:], task, [], opts).blockingGet()
		}

		// Task Option Types
        String peIntegration = task.taskOptions.find { it.optionType.code == 'puppetEnterpriseIntegration' }?.value
        String boltTask = task.taskOptions.find { it.optionType.code == 'boltTask' }?.value
        String boltTaskParameters = task.taskOptions.find { it.optionType.code == 'boltTaskParams' }?.value

		// Fetch integration configuration
		def integration = context.async.accountIntegration.find(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", peIntegration))).blockingGet()
		JsonSlurper slurper = new JsonSlurper()
		def integrationJson = slurper.parseText(integration.config)

		// Set Puppet Enterprise integration variables
		def accessToken = integrationJson.cm.plugin.serviceToken
		def serviceUrl = integrationJson.cm.plugin.serviceUrl
		def ignoreVerifySSL = integrationJson.cm.plugin.ignoreVerifySSL
		def orchestratorPort = integrationJson.cm.plugin.peOrchestratorPort
		def puppetDbPort = integrationJson.cm.plugin.puppetdbPort
		def peNodeClassifierPort = integrationJson.cm.plugin.peNodeClassifierPort
		def useCache = integrationJson.cm.plugin.peDataCache

		// Lookup the instance certname
		def instanceMapping = context.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('refType', "Instance"),
				new DataFilter('refId', instance.id),
				new DataFilter('name', "instance-tab-ui")
		])).blockingGet()

		// Set HTTP client settings for authenticating to Puppet Enterprise
		HttpApiClient client = new HttpApiClient()
        def taskPayload = [:]
        taskPayload["environment"] = "production"
        taskPayload["task"] = boltTask
        taskPayload["params"] = [:]
        taskPayload["scope"] = [:]
        taskPayload["scope"]["nodes"] = ["${instanceMapping.keyValue}"]
        log.info "Task PAYLOAD: ${taskPayload}"
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: ignoreVerifySSL, body: taskPayload)
		def account = new Account(id: config.accountId)

		def results = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/command/task", "", "", requestOptions, 'POST')
        log.info "RESulTEST: ${results.data.job.name}"

        def jobId = results.data.job.name
		HttpApiClient.RequestOptions jobrequestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: ignoreVerifySSL)

		def jobState = "running"
		Integer attempts = 0
		while(jobState == "running") {
			def joboutput = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/jobs/${jobId}/nodes", "", "", jobrequestOptions, 'GET')
			jobState = joboutput.data.items[0].state
			log.info "Job STATE: ${jobState}"
	        sleep(15000)
			if (attempts > 5){
				log.info "Job timedout"
				break;
			}
		}
		def jobresults = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/jobs/${jobId}/nodes", "", "", jobrequestOptions, 'GET')
        def dtrestul = JsonOutput.toJson(jobresults.data.items[0].result).toString()
        new TaskResult(
            success: true,
            data   : "${dtrestul}",
            output : "${dtrestul}"
		)
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task, Map opts) {
		TaskConfig config = buildComputeServerTaskConfig(server, [:], task, [], opts).blockingGet()
		context.executeCommandOnServer(server, 'echo $JAVA_HOME')
		context.executeCommandOnServer(server, 'echo $JAVA_HOME', false, 'user', 'password', null, null, null, false, false)
		executeTask(task, config)
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task) {
		TaskConfig config = buildComputeServerTaskConfig(server, [:], task, [], [:]).blockingGet()
		context.executeCommandOnServer(server, 'echo $JAVA_HOME')
		executeTask(task, config)
	}

	@Override
	TaskResult executeContainerTask(Container container, Task task, Map opts) {
		TaskConfig config = buildContainerTaskConfig(container, [:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME')
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME', 'user', 'password', null, null, null, false, null, false)
		executeTask(task, config)
	}

	@Override
	TaskResult executeContainerTask(Container container, Task task) {
		TaskConfig config = buildContainerTaskConfig(container, [:], task, [], [:]).blockingGet()
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Map opts, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildRemoteTaskConfig([:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME')
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildRemoteTaskConfig([:], task, [], [:]).blockingGet()
		context.executeSshCommand('localhost', 8080, 'bob', 'password', 'echo $JAVA_HOME', null, null, null, false, null, LogLevel.debug, false, null, true)
		executeTask(task, config)
	}

	/**
	 * Finds the input text from the OptionType created in {@link ReverseTextTaskProvider#getOptionTypes}.
	 * Uses Groovy {@link org.codehaus.groovy.runtime.StringGroovyMethods#reverse} on the input text
	 * @param task
	 * @param config
	 * @return data and output are the reversed text
	 */
	TaskResult executeTask(Task task, TaskConfig config) {
		println config.accountId
		def taskOption = task.taskOptions.find { it.optionType.code == 'reverseTextTaskText' }
		String data = taskOption?.value?.reverse()
		new TaskResult(
				success: true,
				data   : data,
				output : data
		)
	}
}