package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.providers.AbstractGenericIntegrationProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.model.OptionType
import com.morpheusdata.views.ViewModel
import com.morpheusdata.model.AccountIntegration
import groovy.util.logging.Slf4j
import com.morpheusdata.model.*
import com.morpheusdata.core.util.RestApiUtil
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.morpheusdata.model.ReferenceData
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions
import com.morpheusdata.core.*

import com.morpheusdata.model.AccountDiscovery
import com.morpheusdata.model.AccountDiscoveryType

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Jedis;


@Slf4j
class PuppetEnterpriseIntegrationProvider extends AbstractGenericIntegrationProvider {
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	PuppetEnterpriseIntegrationProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	String code = 'puppet-enterprise-integration'
	String name = 'Puppet Enterprise'

    @Override
    MorpheusContext getMorpheus() {
		return this.morpheusContext
    }

    @Override
    Plugin getPlugin() {
        return plugin
    }

    @Override
    String getCode() {
        return code
    }

    @Override
    String getName() {
        return name
    }

	@Override
	String getCategory() {
		return "other"
	}

	@Override
	Icon getIcon() {
		return new Icon(path:"puppet-enterprise.png", darkPath: "puppet-enterprise.png")
	}

	@Override
	void refresh(AccountIntegration accountIntegration) {
		log.info "Starting sync for Puppet Enterprise integration"

		// Parse integration JSON payload
		JsonSlurper slurper = new JsonSlurper()
		def integrationJson = slurper.parseText(accountIntegration.config)

		// Set Puppet Enterprise integration variables
		def accessToken = integrationJson.cm.plugin.serviceToken
		def serviceUrl = integrationJson.cm.plugin.serviceUrl
		def ignoreVerifySSL = integrationJson.cm.plugin.ignoreVerifySSL
		def orchestratorPort = integrationJson.cm.plugin.peOrchestratorPort
		def puppetDbPort = integrationJson.cm.plugin.puppetdbPort
		def peNodeClassifierPort = integrationJson.cm.plugin.peNodeClassifierPort
		def useCache = integrationJson.cm.plugin.peDataCache
		def peDataCacheHost = integrationJson.cm.plugin.peDataCacheHost
		def peDataCachePort = integrationJson.cm.plugin.peDataCachePort

		// Set HTTP client settings for authenticating to Puppet Enterprise
		HttpApiClient client = new HttpApiClient()
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: ignoreVerifySSL)
		def account = accountIntegration.account

		// Sync Puppet Bolt Tasks
		log.info "Syncing puppet enterprise bolt tasks"
		def results = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/tasks", "", "", requestOptions, 'GET')
		def boltTasks = results.data.items
		def tasks = []
		def taskAdds = []
		for(task in boltTasks){
			tasks << task["name"]
		}
		/// Create JSON payload for tasks
		def configPayload = [:]
		configPayload["tasks"] = tasks
		def configOutput = JsonOutput.toJson(configPayload)
		def tasksMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.bolt.tasks.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (tasksMatch){
			tasksMatch.config = configOutput
			morpheusContext.async.referenceData.save(tasksMatch).blockingGet()
		} else {		
			def variableConfig = [account:account, 
					code: "puppetenterprise.bolt.tasks.${accountIntegration.id}", 
					category:"puppetenterprise", 
					keyValue: "bolt-tasks", 
					description: "puppet enterprise tasks",
					name: "bolt-tasks", 
					value: "bolt-tasks", 
					refType: 'AccountIntegration',
					type: 'string', 
					refId: accountIntegration.id,
					config: configOutput
				]
			def add = new ReferenceData(variableConfig)
			taskAdds << add
			morpheusContext.referenceData.create(taskAdds).blockingGet()
		}

		// Sync Puppet Bolt Plans
		log.info "Syncing puppet enterprise bolt plans"
		def planResults = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/plans", "", "", requestOptions, 'GET')
		def boltPlans = planResults.data.items
		def plans = []
		def planAdds = []

		for(plan in boltPlans){
			plans << plan["name"]
		}
		def plansConfigPayload = [:]
		plansConfigPayload["plans"] = plans
		def plansConfigOutput = JsonOutput.toJson(plansConfigPayload)
		def plansMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.bolt.plans.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (plansMatch){
			plansMatch.config = plansConfigOutput
			morpheusContext.async.referenceData.save(plansMatch).blockingGet()
		} else {		
			def planVariableConfig = [account:account, 
				code: "puppetenterprise.bolt.plans.${accountIntegration.id}", 
				category:"puppetenterprise", 
				keyValue: "bolt-plans", 
				description: "puppet enterprise plans",
				name: "bolt-plans", 
				value: "bolt-plans", 
				refType: 'AccountIntegration',
				type: 'string', 
				refId: accountIntegration.id,
				config: plansConfigOutput
				]
			def planAdd = new ReferenceData(planVariableConfig)
			planAdds << planAdd
			morpheusContext.referenceData.create(planAdds).blockingGet()
		}

		// Sync Puppet Nodes
		log.info "Syncing puppet enterprise nodes"
		def nodeResults = client.callJsonApi("${serviceUrl}:${puppetDbPort}", "pdb/query/v4/nodes", "", "", requestOptions, 'GET')
		def nodes = nodeResults.data
		def nodeAdds = []
		def nodesConfigPayload = [:]
		nodesConfigPayload["nodes"] = nodes
		def nodesConfigOutput = JsonOutput.toJson(nodesConfigPayload)
		def nodesMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.nodes.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (nodesMatch){
			nodesMatch.config = nodesConfigOutput
			morpheusContext.async.referenceData.save(nodesMatch).blockingGet()
		} else {		
			def nodeVariableConfig = [
					account:account, 
					code: "puppetenterprise.nodes.${accountIntegration.id}", 
					category:"puppetenterprise", 
					keyValue: "nodes", 
					description: "puppet enterprise nodes",
					name: "nodes", 
					value: "nodes", 
					refType: 'AccountIntegration',
					type: 'string', 
					refId: accountIntegration.id,
					config: nodesConfigOutput
				]
			def nodeAdd = new ReferenceData(nodeVariableConfig)
			nodeAdds << nodeAdd
			morpheusContext.referenceData.create(nodeAdds).blockingGet()
		}

		// Sync Puppet Node Groups
		log.info "Syncing puppet enterprise node groups"
		def nodeGroupResults = client.callJsonApi("${serviceUrl}:${peNodeClassifierPort}", "classifier-api/v1/groups", "", "", requestOptions, 'GET')
		def nodeGroups = nodeGroupResults.data
		def groups = []
		def groupAdds = []

		for(nodeGroup in nodeGroups){
			groups << nodeGroup["name"]
		}
		def nodeGroupsConfigPayload = [:]
		nodeGroupsConfigPayload["groups"] = groups
		def nodeGroupsConfigOutput = JsonOutput.toJson(nodeGroupsConfigPayload)
		def nodeGroupsMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.nodegroups.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (nodeGroupsMatch){
			nodeGroupsMatch.config = nodeGroupsConfigOutput
			morpheusContext.async.referenceData.save(nodeGroupsMatch).blockingGet()
		} else {		
			def nodeGroupVariableConfig = [account:account, 
				code: "puppetenterprise.nodegroups.${accountIntegration.id}", 
				category:"puppetenterprise", 
				keyValue: "node-groups", 
				description: "puppet enterprise node groups",
				name: "node-groups", 
				value: "node-groups", 
				refType: 'AccountIntegration',
				type: 'string', 
				refId: accountIntegration.id,
				config: nodeGroupsConfigOutput
				]
			def nodeGroupAdd = new ReferenceData(nodeGroupVariableConfig)
			groupAdds << nodeGroupAdd
			morpheusContext.referenceData.create(groupAdds).blockingGet()
		}

		// Sync PE Services Status
		log.info "Syncing puppet enterprise service status"
		def serviceStatusResults = client.callJsonApi("${serviceUrl}:${peNodeClassifierPort}", "status/v1/services", "", "", requestOptions, 'GET')
		def status = serviceStatusResults.data
		def statusAdds = []
		def statusConfigOutput = JsonOutput.toJson(status)
		def statusMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.status.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (statusMatch){
			statusMatch.config = statusConfigOutput
			morpheusContext.async.referenceData.save(statusMatch).blockingGet()
		} else {		
			def statusVariableConfig = [account:account, 
				code: "puppetenterprise.status.${accountIntegration.id}", 
				category:"puppetenterprise", 
				keyValue: "status", 
				description: "puppet enterprise status",
				name: "status", 
				value: "status", 
				refType: 'AccountIntegration',
				type: 'string', 
				refId: accountIntegration.id,
				config: statusConfigOutput
				]
			def statusAdd = new ReferenceData(statusVariableConfig)
			statusAdds << statusAdd
			morpheusContext.referenceData.create(statusAdds).blockingGet()
		}

		if (useCache) {
			// Add redis connection for data caching
			JedisPooled jedis = new JedisPooled(peDataCacheHost, 6379);
			// Sync Puppet Facts
			def factsResults = client.callJsonApi("${serviceUrl}:${puppetDbPort}", "pdb/query/v4/inventory", "", "", requestOptions, 'GET')
			def facts = factsResults.data
			def factsAdds = []
			def factsConfigOutput = JsonOutput.toJson(facts)

			def instancesMatch = morpheusContext.async.referenceData.list(new DataQuery().withFilters([
					new DataFilter('refType', "Instance"),
					new DataFilter('name', "instance-tab-ui")
			])).toList().blockingGet()

			instancesMatch.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				def factsInfo = ""
				for (node in facts){
					log.info "Found Node ${rd.keyValue}"
					if (node.certname == rd.keyValue){
						factsInfo = node.facts
					}
				}
				def instjson = JsonOutput.toJson(factsInfo)
				jedis.set(rd.keyValue, instjson);
			}
		}

		// Alarms
		for (node in nodes){
			// Evaluate if the status warrants an alarm
			if (node.latest_report_status != "unchanged"){
				def alarmadds = []
				def alarmConfig = [
					account:			account,
					category:			"integration",
					name:				"Puppet Enterprise: The instance configuration is out of sync - ${node.latest_report_status}",
					eventKey:			"PE Drift",
					acknowledged:		false,
					acknowledgedDate:	null,
					acknowledgedByUser: null,
					status:				"warning",
					statusMessage:		"Drifted",
					resourceName:		"${node.certname}",
					refType:			"computeServer",
					refId: 				1,
					startDate:			new Date()
				]
				def alarmadd = new OperationNotification(alarmConfig)
				alarmadds << alarmadd
				if(alarmadds) {
					morpheusContext.async.operationNotification.create(alarmadds).blockingGet()
				}
			}
		}
	}

	@Override
	List<OptionType> getOptionTypes() {
		OptionType apiUrl = new OptionType(
			name: 'Puppet Enterprise API Endpoint',
			code: 'puppet-enterprise-api',
			fieldName: 'serviceUrl',
			helpText: 'Warning! Using HTTP URLS are insecure and not recommended.',
			displayOrder: 0,
			fieldLabel: 'URL',
			required: true,
			inputType: OptionType.InputType.TEXT
		)
		OptionType ignoreVerifySSL = new OptionType(
			name: 'Puppet Enterprise Verify SSL Certificate',
			code: 'puppet-enterprise-verify-ssl',
			fieldName: 'ignoreVerifySSL',
			defaultValue: 'off',
			displayOrder: 1,
			fieldLabel: 'Ignore Verify SSL Certificate',
			inputType: OptionType.InputType.CHECKBOX
		)
        OptionType credentials = new OptionType(
			name: 'Credentials',
			code: 'puppetenterprise.credentials',
			inputType: OptionType.InputType.CREDENTIAL,
			fieldName: 'type',
			fieldLabel: 'Credentials',
			fieldContext: 'credential',
			required: true,
			defaultValue: 'local',
			optionSource: 'credentials',
			displayOrder: 2,
			config: '{"credentialTypes":["api-key"]}'
		)
		OptionType apiPassword = new OptionType(
			name: 'Puppet Enterprise Access Token',
			code: 'puppet-enterprise-access-token',
			fieldName: 'serviceToken',
			displayOrder: 3,
			fieldLabel: 'Access Token',
			required: true,
			localCredential: true,
			inputType: OptionType.InputType.PASSWORD
		)
		OptionType puppetDbPort = new OptionType(
			name: 'Puppet Enterprise PuppetDB Port',
			code: 'puppet-enterprise-puppetdb-port',
			fieldName: 'puppetdbPort',
			defaultValue: '8081',
			displayOrder: 4,
			fieldLabel: 'PuppetDB Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType peOrchestratorPort = new OptionType(
			name: 'Puppet Enterprise Orchestrator Port',
			code: 'puppet-enterprise-orchestrator-port',
			fieldName: 'peOrchestratorPort',
			defaultValue: '8143',
			displayOrder: 5,
			fieldLabel: 'Orchestrator Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType peNodeClassifierPort = new OptionType(
			name: 'Puppet Enterprise Node Classifier Port',
			code: 'puppet-enterprise-node-classifier-port',
			fieldName: 'peNodeClassifierPort',
			defaultValue: '4433',
			displayOrder: 6,
			fieldLabel: 'Node Classifier Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType controlRepository = new OptionType(
			name: 'Puppet Enterprise Control Repository',
			code: 'puppet-enterprise-control-repository',
			fieldName: 'controlRepository',
			displayOrder: 7,
			fieldLabel: 'Control Repository URL',
			required: false,
			inputType: OptionType.InputType.TEXT
		)
		OptionType useCache = new OptionType(
			name: 'Puppet Enterprise Data Cache',
			code: 'puppet-enterprise-data-cache',
			fieldName: 'peDataCache',
			fieldGroup: 'Cache Settings',
			helpText: 'Use a Redis caching server to improve data fetching performance for UI components',
			displayOrder: 8,
			fieldLabel: 'Use Cache',
			defaultValue: 'off',
			required: false,
			inputType: OptionType.InputType.CHECKBOX
		)
		OptionType cacheHost = new OptionType(
			name: 'Puppet Enterprise Cache Host',
			code: 'puppet-enterprise-data-cache-host',
			fieldName: 'peDataCacheHost',
			fieldGroup: 'Cache Settings',
			displayOrder: 9,
			defaultValue: 'localhost',
			fieldLabel: 'Cache Host',
			required: false,
			inputType: OptionType.InputType.TEXT
		)
		OptionType cachePort = new OptionType(
			name: 'Puppet Enterprise Cache Port',
			code: 'puppet-enterprise-data-cache-port',
			fieldName: 'peDataCachePort',
			fieldGroup: 'Cache Settings',
			defaultValue: '6379',
			displayOrder: 10,
			fieldLabel: 'Cache Port',
			required: false,
			inputType: OptionType.InputType.TEXT
		)
		return [apiUrl, ignoreVerifySSL, credentials, puppetDbPort, peOrchestratorPort, peNodeClassifierPort, apiPassword, controlRepository, useCache, cacheHost, cachePort]
	}

	@Override
	HTMLResponse renderTemplate(AccountIntegration integration) {
		// Define an object for storing the data retrieved from the Puppet Enterprise REST API
		def HashMap<String, String> puppetEnterprisePayload = new HashMap<String, String>();

		// Parse the integration configuration
		JsonSlurper slurper = new JsonSlurper()
		def integrationJson = slurper.parseText(integration.config)
		// Fetch node data from the Morpheus database
		List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.nodes.${integration.id}")).toList().blockingGet()
		def nodes = []
		peReferenceData.each { alrd ->
			def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
			if ( rd.config != null && !rd.config.isEmpty() ) {
				def json = slurper.parseText(rd.config)
				nodes = json.nodes
			}
		}

		// Fetch service status from the Morpheus database
		List<com.morpheusdata.model.ReferenceData> peStatusReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.status.${integration.id}")).toList().blockingGet()
		def out = [:]
		peStatusReferenceData.each { alrd ->
			def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
			if ( rd.config != null && !rd.config.isEmpty() ) {
				def json = slurper.parseText(rd.config)
				out = json
			}
		}

		def peversion = ""
		if (out["pe-console"] != null){
			peversion = out["pe-console"]["service_version"]
		}

		// Evaluate whether the Puppet control repository setting for the
		// integration has been defined to determine whether the "open control repository"
		// button on the actions drop-down will show or not
		def controlRepoDefined = false
		def controlRepoUrl = ""
		log.info "IG CM: ${integrationJson.cm}"
		log.info "IG CM Plugin: ${integrationJson.cm.plugin}"
		log.info "IG CM Plugin CR: ${integrationJson.cm.plugin.controlRepository}"
		if (integrationJson.cm.plugin.controlRepository == null){
			controlRepoDefined = false
			controlRepoUrl = integrationJson.cm.plugin.controlRepository
		} else {
			controlRepoDefined = true
			controlRepoUrl = integrationJson.cm.plugin.controlRepository
		}

		Long unchangedCount = 0
		Long correctiveChanges = 0
		Long intentionalChanges = 0
		Long failures = 0
		for (node in nodes){
			switch(node.latest_report_status) {
				case "changed":
					correctiveChanges++
					break;
				case "unchanged":
					unchangedCount++
					break;
				default:
				    println "Task with no icon found"
			}
		}

		// Fetch Bolt Tasks and Plans
		def automationItems = []

		List<com.morpheusdata.model.ReferenceData> plansReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.plans.${integration.id}")).toList().blockingGet()
		plansReferenceData.each { alrd ->
			def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
			if ( rd.config != null && !rd.config.isEmpty() ) {
				def plansjson = slurper.parseText(rd.config)
				for (plan in plansjson.plans){
					def bt = [:]
					bt["name"] = plan
					bt["type"] = "plan"
					automationItems << bt
				}
			}
		}

		List<com.morpheusdata.model.ReferenceData> tasksReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.tasks.${integration.id}")).toList().blockingGet()
		tasksReferenceData.each { alrd ->
			def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
			if ( rd.config != null && !rd.config.isEmpty() ) {
				def tasksJson = slurper.parseText(rd.config)
				for (task in tasksJson.tasks){
					def bt = [:]
					bt["name"] = task
					bt["type"] = "task"
					automationItems << bt
				}
			}
		}

		def naturallyOrderedMap = automationItems.sort({it1, it2 -> it1.value <=> it1.value})
		
		// Add webnonce to use JavaScript in the rendered template
		def webnonce = morpheus.getWebRequest().getNonceToken()
		puppetEnterprisePayload.put("webnonce",webnonce)

		puppetEnterprisePayload.put("automation", naturallyOrderedMap)
		puppetEnterprisePayload.put("pe_server", integrationJson.cm.plugin.serviceUrl)
		puppetEnterprisePayload.put("pe_version", peversion)
		puppetEnterprisePayload.put("nodes", nodes)
		puppetEnterprisePayload.put("node_count", nodes.size)
		puppetEnterprisePayload.put("unchanged_count", unchangedCount)
		puppetEnterprisePayload.put("corrective_change_count", correctiveChanges)
		puppetEnterprisePayload.put("control_repo_url", controlRepoUrl)
		puppetEnterprisePayload.put("control_repo_defined", controlRepoDefined)

		ViewModel<HashMap> model = new ViewModel<>()
        model.object = puppetEnterprisePayload
		getRenderer().renderTemplate("hbs/puppetEnterpriseIntegration", model)
	}

	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp.scriptSrc = '*.jsdelivr.net'
		csp.styleSrc = '*.jsdelivr.net'
		csp
	}
}