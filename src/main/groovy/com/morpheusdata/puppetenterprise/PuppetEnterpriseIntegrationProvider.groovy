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
		log.info "Puppet Enterprise Guidance Recommendations"
		log.info("Running Puppet Enterprise Checks")
        List<AccountDiscovery> discoveriesToAdd = []
        AccountDiscovery discovery = new AccountDiscovery()
        discovery.type = new AccountDiscoveryType()
		discovery.type.code = "puppet-enterprise-guidance"
		discovery.type.name = "Puppet Enterprise"
		discovery.type.canExecute = true
		discovery.type.title = "PE Drift Detection"
        discovery.refType = "computeServer"
        discovery.refId = 1
        discovery.refName = "modemom01"
		discovery.savings = 0.00
        //discovery.configMap = [agentVersion:computeServer.serverOs?.platform == PlatformType.windows ? latestWindowsAgentVersion : latestLinuxAgentVersion]
        discovery.severity = "info"
        discovery.actionTitle = "puppet"
        discovery.actionType = "puppet"
        discovery.actionCategory = "agent"
        discovery.actionMessage = "Upgrade Agent to latest version"
        discovery.dateCreated = new Date()
        discovery.lastUpdated = new Date()
        discovery.accountId = 1
        discovery.userId = 1
        discovery.zoneId = 1
        discovery.siteId = 4

        discoveriesToAdd.add(discovery)
        morpheusContext.services.discovery.bulkCreate(discoveriesToAdd)

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

		// Set HTTP client settings for authenticating to Puppet Enterprise
		HttpApiClient client = new HttpApiClient()
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: ignoreVerifySSL)
		def account = accountIntegration.account

		// Sync Puppet Bolt Tasks
		def results = client.callJsonApi("${serviceUrl}:${orchestratorPort}", "orchestrator/v1/tasks", "", "", requestOptions, 'GET')
		def boltTasks = results.data.items
		def tasks = []
		def adds = []
		log.info "BOLT TASK RESULTS ${results}"
		for(task in boltTasks){
			tasks << task["name"]
		}
		def configPayload = [:]
		configPayload["tasks"] = tasks
		def configOutput = JsonOutput.toJson(configPayload)
		def regionMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.bolt.tasks.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (regionMatch){
			regionMatch.config = configOutput
			morpheusContext.referenceData.create(regionMatch).blockingGet()
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
			adds << add
			morpheusContext.referenceData.create(adds).blockingGet()
		}


		// Sync Puppet Bolt Plans
		log.info "Sync Puppet Bolt Plans"
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
			morpheusContext.referenceData.create(plansMatch).blockingGet()
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
		def nodeResults = client.callJsonApi("${serviceUrl}:${puppetDbPort}", "pdb/query/v4/nodes", "", "", requestOptions, 'GET')
		def nodes = nodeResults.data
		def nodeAdds = []
		def nodesConfigPayload = [:]
		nodesConfigPayload["nodes"] = nodes
		def nodesConfigOutput = JsonOutput.toJson(nodesConfigPayload)
		log.info "PE NODES DATA: ${nodesConfigOutput}"
		def nodesMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.nodes.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (nodesMatch){
			nodesMatch.config = nodesConfigOutput
			morpheusContext.referenceData.create(nodesMatch).blockingGet()
		} else {		
			def nodeVariableConfig = [account:account, 
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
		def nodeGroupResults = client.callJsonApi("${serviceUrl}:${peNodeClassifierPort}", "classifier-api/v1/groups", "", "", requestOptions, 'GET')
		def nodeGroups = nodeGroupResults.data
		log.info "PE NODE GROUPS DATA: ${nodeGroups}"
		/*
		def nodeGroupsData = []
		def nodeGroupAdds = []

		for(nodeGroup in nodeGroups){
			nodeGroupsData << nodeGroups["name"]
		}
		def plansConfigPayload = [:]
		plansConfigPayload["plans"] = plans
		def plansConfigOutput = JsonOutput.toJson(plansConfigPayload)
*/
		// Sync PE Services Status
		def serviceStatusResults = client.callJsonApi("${serviceUrl}:4433", "status/v1/services", "", "", requestOptions, 'GET')
		def status = serviceStatusResults.data
		def statusAdds = []
		def statusConfigOutput = JsonOutput.toJson(status)
		def statusMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.status.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (statusMatch){
			statusMatch.config = statusConfigOutput
			morpheusContext.referenceData.create(statusMatch).blockingGet()
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
			JedisPooled jedis = new JedisPooled("localhost", 6379);
			// Sync Puppet Facts
			def factsResults = client.callJsonApi("${serviceUrl}:${puppetDbPort}", "pdb/query/v4/inventory", "", "", requestOptions, 'GET')
			def facts = factsResults.data
			def factsAdds = []
			def factsConfigOutput = JsonOutput.toJson(facts)
			def instances = morpheusContext.async.instance.list().toList().blockingGet()
			for (instance in instances){
				def factsInfo = ""
				for (node in facts){
					def certname = "${instance.name}.grt.local"
					if (node.certname == certname){
						factsInfo = node.facts
					}
				}
				def instjson = JsonOutput.toJson(factsInfo)
				jedis.set(instance.name, instjson);
			}
		}


		/*
		def factsMatch = morpheusContext.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.facts.${accountIntegration.id}"),
		])).blockingGet()
		// Check if there's an existing ref data entry
		if (nodesMatch){
			nodesMatch.config = nodesConfigOutput
			morpheusContext.referenceData.create(nodesMatch).blockingGet()
		} else {		
			def nodeVariableConfig = [account:account, 
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
		}*/

		/*
		def alarmadds = []
		String zoneCategory = "puppetenterprise.drift.alarm"
		Date date = new Date(); 
		def alarmConfig = [account:account, category:zoneCategory, name:"Puppet Enterprise Drift",
			   eventKey:"PE Drift", externalId:817,
			   acknowledged:false,
			   acknowledgedDate:null, acknowledgedByUser:null,
			   status:"warning", statusMessage:"Drifted",
			   resourceName:"grtpe01.grt.local", refType:'instance', refId: 817,
			   uniqueId:817,startDate:date]
		def alarmadd = new OperationNotification(alarmConfig)
		alarmadds << alarmadd
		if(alarmadds) {
			morpheusContext.async.operationNotification.create(alarmadds).blockingGet()
		}
		*/

		accountIntegration.setServiceConfig("Testinstuff, sync complete")
		morpheus.async.accountIntegration.save(accountIntegration).subscribe().dispose()
		sleep(3000)

		log.debug "daily refresh run for ${accountIntegration}"
	}

	@Override
	List<OptionType> getOptionTypes() {
		OptionType apiUrl = new OptionType(
			name: 'Puppet Enterprise API Endpoint',
			code: 'puppet-enterprise-api',
			fieldName: 'serviceUrl',
			placeHolder: 'https://demope01.test.local',
			defaultValue: 'https://grtpe01.grt.local',
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
		OptionType puppetDbPort = new OptionType(
			name: 'Puppet Enterprise PuppetDB Port',
			code: 'puppet-enterprise-puppetdb-port',
			fieldName: 'puppetdbPort',
			defaultValue: '8081',
			displayOrder: 2,
			fieldLabel: 'PuppetDB Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType peOrchestratorPort = new OptionType(
			name: 'Puppet Enterprise Orchestrator Port',
			code: 'puppet-enterprise-orchestrator-port',
			fieldName: 'peOrchestratorPort',
			defaultValue: '8143',
			displayOrder: 3,
			fieldLabel: 'Orchestrator Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType peNodeClassifierPort = new OptionType(
			name: 'Puppet Enterprise Node Classifier Port',
			code: 'puppet-enterprise-node-classifier-port',
			fieldName: 'peNodeClassifierPort',
			defaultValue: '4433',
			displayOrder: 4,
			fieldLabel: 'Node Classifier Port',
			inputType: OptionType.InputType.TEXT
		)
		OptionType apiPassword = new OptionType(
			name: 'Puppet Enterprise Access Token',
			code: 'puppet-enterprise-access-token',
			fieldName: 'serviceToken',
			displayOrder: 5,
			defaultValue: 'ANh0f7LhH3fL86MbdiJJe0-MkHwGNR2Syn0bTX2F7HK1',
			fieldLabel: 'Access Token',
			required: true,
			inputType: OptionType.InputType.PASSWORD
		)
		OptionType controlRepository = new OptionType(
			name: 'Puppet Enterprise Control Repository',
			code: 'puppet-enterprise-control-repository',
			fieldName: 'controlRepository',
			displayOrder: 6,
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
			displayOrder: 7,
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
			displayOrder: 8,
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
			displayOrder: 9,
			fieldLabel: 'Cache Port',
			required: false,
			inputType: OptionType.InputType.TEXT
		)
		return [apiUrl, ignoreVerifySSL, puppetDbPort, peOrchestratorPort, peNodeClassifierPort, apiPassword, controlRepository, useCache, cacheHost, cachePort]
	}

	@Override
	HTMLResponse renderTemplate(AccountIntegration integration) {

		// Define an object for storing the data retrieved from the Puppet Enterprise REST API
		def HashMap<String, String> puppetEnterprisePayload = new HashMap<String, String>();

		// Add webnonce to use JavaScript in the rendered template
		def webnonce = morpheus.getWebRequest().getNonceToken()
		puppetEnterprisePayload.put("webnonce",webnonce)

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
			log.info "SERVICD DATAUS: ${peversion}"
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

		List<com.morpheusdata.model.ReferenceData> plansReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.plans.${integration.id}")).toList().blockingGet()
		log.info "PLANS REF DATA ${plansReferenceData}"
		def plans = []
		plansReferenceData.each { alrd ->
			def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
			log.info "Config ${rd}"
			log.info "Integration: ${rd.config}"
			if ( rd.config != null && !rd.config.isEmpty() ) {
				//JsonSlurper slurper = new JsonSlurper()
				def plansjson = slurper.parseText(rd.config)
				log.info "JSON PAYLOAD: ${plansjson}"
				for (plan in plansjson.plans){
					def bt = [:]
					bt["name"] = plan
					bt["value"] = plan
					plans << bt
				}
			} else {
				def bt = [:]
				bt["name"] = rd.value
				bt["value"] = rd.value
				plans << bt
			}
		}

		puppetEnterprisePayload.put("plans", plans)
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
		csp.frameSrc = '*.digitalocean.com'
		csp.imgSrc = '*.wikimedia.org'
		csp.styleSrc = '*.jsdelivr.net'
		csp
	}
}