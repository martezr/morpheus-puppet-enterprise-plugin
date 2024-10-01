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

/**
 * Example AbstractTaskService. Each method demonstrates building an example TaskConfig for the relevant task type
 */
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
        log.info "Local tetK ${task}"
        log.info "Local tetK ${opts}"

		TaskConfig config = buildLocalTaskConfig([:], task, [], opts).blockingGet()
        log.info "Local tetK ${config}"

		if(instance) {
			config = buildInstanceTaskConfig(instance, [:], task, [], opts).blockingGet()
		}
		if(container) {
			config = buildContainerTaskConfig(container, [:], task, [], opts).blockingGet()
		}
	
        println config.accountId
        String peIntegration = task.taskOptions.find { it.optionType.code == 'puppetEnterpriseIntegration' }?.value
        String boltTask = task.taskOptions.find { it.optionType.code == 'boltTask' }?.value
        log.info "integration: ${peIntegration}"
        log.info "boltTask: ${boltTask}"

		def integrations = context.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", peIntegration))).toList().blockingGet()
		def serviceURL = ""
        def accessToken = ""
        for(integration in integrations){
			log.info "Integration: ${integration.config}"
			JsonSlurper slurper = new JsonSlurper()
			def settingsJson = slurper.parseText(integration.config)
			serviceURL = settingsJson.cm.plugin.serviceUrl
            accessToken = settingsJson.cm.plugin.serviceToken
			log.info "Service URL: ${serviceURL}"
		}
		HttpApiClient client = new HttpApiClient()
        def taskPayload = [:]
        taskPayload["environment"] = "production"
        taskPayload["task"] = boltTask
        taskPayload["task"] = boltTask
        taskPayload["params"] = [:]
        taskPayload["scope"] = [:]
        taskPayload["scope"]["nodes"] = ["grtpe01.grt.local"]
        log.info "Task PAYLOAD: ${taskPayload}"
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: true, body: taskPayload)
		def account = new Account(id: 1)

		// Sync Puppet Bolt Tasks
		//
		def results = client.callJsonApi("https://grtpe01.grt.local:8143", "orchestrator/v1/command/task", "", "", requestOptions, 'POST')
		def boltTasks = results
        log.info "RESulTEST: ${results.data.job.name}"

        def jobId = results.data.job.name
		HttpApiClient.RequestOptions jobrequestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: true)

        sleep(10000)
		def jobresults = client.callJsonApi("https://grtpe01.grt.local:8143", "orchestrator/v1/jobs/${jobId}/nodes", "", "", jobrequestOptions, 'GET')
        log.info "JOB RESULT: ${jobresults.data.items}"
        def dtrestul = JsonOutput.toJson(jobresults.data.items[0].result).toString()
        new TaskResult(
            success: true,
            data   : "${dtrestul}",
            output : "${dtrestul}"
		)
		//executeTask(task, config)
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