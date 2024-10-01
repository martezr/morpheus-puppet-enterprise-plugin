package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.AbstractInstanceTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Account
import com.morpheusdata.model.Instance
import com.morpheusdata.model.User
import com.morpheusdata.model.TaskConfig
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Jedis;
import groovy.util.logging.Slf4j

@Slf4j
class PuppetEnterpriseInstanceTabProvider extends AbstractInstanceTabProvider {
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	PuppetEnterpriseInstanceTabProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}

	/**
	 * Instance details provided to your rendering engine
	 * @param instance details of an Instance
	 * @return result of rendering an template
	 */
	@Override
	HTMLResponse renderTemplate(Instance instance) {

		// Instantiate an object for storing data
		// passed to the html template
		ViewModel<Instance> model = new ViewModel<>()
		
		// Retrieve additional details about the instance
        // https://developer.morpheusdata.com/api/com/morpheusdata/model/TaskConfig.InstanceConfig.html
		TaskConfig instanceDetails = morpheus.buildInstanceConfig(instance, [:], null, [], [:]).blockingGet()

		// Define an object for storing the data retrieved
		// from the DataDog REST API
		def HashMap<String, String> puppetEnterprisePayload = new HashMap<String, String>();

		def webnonce = morpheus.getWebRequest().getNonceToken()
		puppetEnterprisePayload.put("webnonce",webnonce)

		JedisPooled jedis = new JedisPooled("localhost", 6379);
		def facts = jedis.get(instance.name)
		log.info "IN FACTS: ${facts}"
		// Check if the returned payload is empty
		if (!facts?.trim()){
			log.info "NON EMPTY Payload"
		} else {
			log.info "EMPTY PAYLOAD"
		}

		//if (!facts?.trim()){	
			log.info "FOUND DFACE"
			JsonSlurper slurper = new JsonSlurper()
			def teso = slurper.parseText(facts)
			def factsOut = []
			def factList = []
			teso.keySet().eachWithIndex { key, index ->
				factList << key
			}

			for (factName in factList.sort()){
				def factdata = [:]
				factdata["name"] = factName
				factdata["value"] = teso[factName]
				factsOut << factdata
			}
			puppetEnterprisePayload.put("facts",factsOut)
			puppetEnterprisePayload.put("facts_json",facts)
		//}

		//def results = puppetEnterpriseAPI.callApi("https://${settingsJson.peHost}:${settingsJson.puppetdbPort}", "pdb/query/v4/reports", "", "", new RestApiUtil.RestOptions(headers:['X-Authentication':settingsJson.peToken,'Content-Type':'application/json'],body:['query':'["=", "certname", "'+ instance.hostName +'.grt.local"]','limit':'5','order_by':[["field":"producer_timestamp","order":"desc"]]],ignoreSSL: true), 'POST')
		//def factsResults = puppetEnterpriseAPI.callApi("https://${settingsJson.peHost}:${settingsJson.puppetdbPort}", "pdb/query/v4/factsets", "", "", new RestApiUtil.RestOptions(headers:['X-Authentication':settingsJson.peToken,'Content-Type':'application/json'],body:['query':'["=", "certname", "'+ instance.hostName +'.grt.local"]'],ignoreSSL: true), 'POST')

/*
		// Parse the JSON response payload returned
		// from the REST API call.
        println "Results: ${results.content}"
		def json = slurper.parseText(results.content)
        //println "Output ${json}"
		println "Facts: ${factsResults.content}"
		def factsJson = slurper.parseText(factsResults.content)


		// Evaluate whether the query returned a host.
		// If the host was not found then render the HTML template
		// for when a host isn't found
        
		if (json.size == 0){
			getRenderer().renderTemplate("hbs/instanceNotFoundTab", model)
		} else {
			// Store objects from the response payload
			def latestReport = json[0]
			def status = latestReport.status
			def resources = latestReport.resources.data.size
			def environment = latestReport.environment
			def agentversion = latestReport.puppet_version
			def rectime = latestReport.receive_time

			def dataOut = []
			json.each{
				def demo = it.metrics.data
				def reportMap = [:]
				def isChanged = false
				def isFailed = false
				def isNoChange = false
				demo.each{
					println it
					if (it['name'] == "total" && it['category'] == "resources"){
						println it['value']
						reportMap['total'] = it['value']
					}
					if (it['name'] == "total" && it['category'] == "time"){
						println it['value']
						reportMap['run_time'] = it['value']
					}
					if (it['name'] == "changed"){
						println it['value']
						reportMap['changed'] = it['value']
					}
					if (it['name'] == "failed"){
						println it['value']
						reportMap['failed'] = it['value']
						if (it['value'] > 0){
							isFailed = true
						}
					}
					if (it['name'] == "skipped"){
						println it['value']
						reportMap['skipped'] = it['value']
					}
				}
				if (reportMap['failed'] > 0){
					isFailed = true
				} else if(reportMap['changed'] > 0){
					isChanged = true
				} else {
					isNoChange = true
				}
				reportMap['isChanged'] = isChanged
				reportMap['isNoChange'] = isNoChange
				reportMap['isFailed'] = isFailed
				reportMap['receive_time'] = it.receive_time
				dataOut << reportMap
			}
			println dataOut

			def osfacts = factsJson[0].facts.data[1].value
			def memoryfacts = factsJson[0].facts.data[14].value
			def cpufacts = factsJson[0].facts.data[50].value
			println "memory facts: ${memoryfacts}"
			println "cpu facts: ${cpufacts}"

			// Set the values of the HashMap object (defined on line #51)
			// that will be used to populate the HTML template
			puppetEnterprisePayload.put("status", status)
			puppetEnterprisePayload.put("resources", resources)
			puppetEnterprisePayload.put("environment", environment)
			puppetEnterprisePayload.put("agentversion", agentversion)
			puppetEnterprisePayload.put("rectime", rectime)
			puppetEnterprisePayload.put("reports", dataOut)
			puppetEnterprisePayload.put("osfacts", osfacts)
			puppetEnterprisePayload.put("memoryfacts", memoryfacts)
			puppetEnterprisePayload.put("cpufacts", cpufacts)
			puppetEnterprisePayload.put("id", instance.id)
			puppetEnterprisePayload.put("certname",instance.hostName +'.grt.local')

			// Set the value of the model object to the HashMap object
			model.object = puppetEnterprisePayload
			*/
			model.object = puppetEnterprisePayload
			getRenderer().renderTemplate("hbs/instanceTab", model)

		}
	

	/**
	 * Provide logic when tab should be displayed. This logic is checked after permissions are validated.
	 *
	 * @param instance Instance details
	 * @param user current User details
	 * @param account Account details
	 * @return whether the tab should be displayed
	 */
	@Override
	Boolean show(Instance instance, User user, Account account) {
		return true
	}

	/**
	 * Returns the Morpheus Context for interacting with data stored in the Main Morpheus Application
	 *
	 * @return an implementation of the MorpheusContext for running Future based rxJava queries
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	/**
	 * Returns the instance of the Plugin class that this provider is loaded from
	 * @return Plugin class contains references to other providers
	 */
	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	/**
	 * A unique shortcode used for referencing the provided provider. Make sure this is going to be unique as any data
	 * that is seeded or generated related to this provider will reference it by this code.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return "morpheus-puppet-enterprise-instanceTab"
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return "Puppet Enterprise"
	}
}
