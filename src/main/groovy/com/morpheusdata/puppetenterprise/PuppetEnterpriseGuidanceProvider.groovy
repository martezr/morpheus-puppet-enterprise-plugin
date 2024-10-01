package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataOrFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.providers.AbstractGuidanceRecommendationProvider
import com.morpheusdata.core.providers.GuidanceRecommendationProvider
import com.morpheusdata.model.AccountDiscovery
import com.morpheusdata.model.AccountDiscoveryType
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.Icon
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.projection.ComputeServerIdentityProjection
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.util.logging.Slf4j

import io.reactivex.rxjava3.schedulers.Schedulers

@Slf4j
class PuppetEnterpriseGuidanceProvider extends AbstractGuidanceRecommendationProvider implements GuidanceRecommendationProvider.ExecutableFacet{
	String code = "puppet-enterprise-guidance"
	String name = "Puppet Enterprise"

	MorpheusContext morpheusContext
	Plugin plugin

	PuppetEnterpriseGuidanceProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	void calculateRecommendations() {
		log.info("Running Puppet Enterprise Checks")
        List<AccountDiscovery> discoveriesToAdd = []
        AccountDiscovery discovery = new AccountDiscovery()
        discovery.type = new AccountDiscoveryType()
		discovery.type.canExecute = true
		discovery.type.code = this.code
		discovery.type.name = this.name
		discovery.type.title = this.name
        discovery.refType = "computeServer"
        discovery.refId = computeServer.id
        discovery.refName = computeServer.name
        //discovery.configMap = [agentVersion:computeServer.serverOs?.platform == PlatformType.windows ? latestWindowsAgentVersion : latestLinuxAgentVersion]
        discovery.severity = "info"
        discovery.actionType = "puppet"
        discovery.actionCategory = "agent"
        discovery.actionMessage = "Upgrade Agent to latest version"
        discovery.dateCreated = new Date()
        discovery.lastUpdated = new Date()
        discovery.accountId = computeServer.account.id
        discovery.userId = computeServer.createdBy?.id
        discovery.zoneId = computeServer.cloud?.id
        discovery.siteId = computeServer.provisionSiteId

        discoveriesToAdd.add(discovery)
        morpheusContext.services.discovery.bulkCreate(discoveriesToAdd)
/*
		List<Cloud> clouds = morpheusContext.services.cloud.list(new DataQuery().withFilters(new DataOrFilter(
						new DataFilter<String>("guidanceMode","==","manual"),
						new DataFilter<String>("guidanceMode","==","auto")
		)))

		List<Long> cloudIds = clouds.collect{it.id} as List<Long>

		String latestLinuxAgentVersion = morpheusContext.services.admin.getAppliance().getLatestLinuxAgentVersion()
		String latestWindowsAgentVersion = morpheusContext.services.admin.getAppliance().getLatestWindowsAgentVersion()
		if(cloudIds) {
			log.info("Cloud IDs: ${cloudIds}")
			morpheusContext.async.computeServer.listIdentityProjections(new DataQuery().withFilters(new DataFilter<List<Long>>("zone.id","in",cloudIds))).buffer(50).observeOn(Schedulers.io()).flatMap { List< ComputeServerIdentityProjection> computeServeridentities ->
				log.info("Found Servers: ${computeServeridentities.size()}")
				morpheusContext.async.computeServer.listById(computeServeridentities.collect{it.id})
				}.filter { ComputeServer computeServer ->
					return computeServer.agentInstalled && ((computeServer.serverOs?.platform != PlatformType.windows && computeServer.agentVersion != latestLinuxAgentVersion) || (computeServer.serverOs?.platform == PlatformType.windows && computeServer.agentVersion != latestWindowsAgentVersion))
				}.buffer(50).doOnNext { List<ComputeServer> computeServers ->
					log.info("Found ${computeServers.size()} servers to upgrade")
					List<AccountDiscovery> existingDiscoveries = morpheusContext.services.discovery.list(new DataQuery().withFilters(new DataFilter<String>("type.code","==",this.code),new DataFilter<String>("refType","computeServer"),new DataFilter<List<Long>>("refId","in",computeServers.collect{it.id})))
					Map<Long,AccountDiscovery> existingDiscoveriesByRefId = existingDiscoveries.collectEntries{[(it.refId):it]}
					List<AccountDiscovery> discoveriesToAdd = []
					computeServers.each { ComputeServer computeServer ->
						if(!existingDiscoveriesByRefId.containsKey(computeServer.id)) {
							AccountDiscovery discovery = new AccountDiscovery()
							discovery.type = new AccountDiscoveryType()
							discovery.type.code = this.code
							discovery.type.name = this.name
							discovery.refType = "computeServer"
							discovery.refId = computeServer.id
							discovery.refName = computeServer.name
							discovery.configMap = [agentVersion:computeServer.serverOs?.platform == PlatformType.windows ? latestWindowsAgentVersion : latestLinuxAgentVersion]
							discovery.severity = "info"
							discovery.actionType = "upgrade"
							discovery.actionCategory = "agent"
							discovery.actionMessage = "Upgrade Agent to latest version"
							discovery.dateCreated = new Date()
							discovery.lastUpdated = new Date()
							discovery.accountId = computeServer.account.id
							discovery.userId = computeServer.createdBy?.id
							discovery.zoneId = computeServer.cloud?.id
							discovery.siteId = computeServer.provisionSiteId

							discoveriesToAdd.add(discovery)
						}
					}
				if(discoveriesToAdd) {
					log.info("Adding ${discoveriesToAdd.size()} discoveries")
					morpheusContext.services.discovery.bulkCreate(discoveriesToAdd)
				}

		}.blockingSubscribe()


		}
		//morpheus.async.cloud.list(new DataQuery())
        */
	}

	@Override
	HTMLResponse renderTemplate(AccountDiscovery accountDiscovery) {
		ViewModel<AccountDiscovery> model = new ViewModel<AccountDiscovery>()
		model.object = accountDiscovery
		getRenderer().renderTemplate("hbs/puppetEnterpiseGuidance", model)
	}

	@Override
	Icon getIcon() {
		return new Icon(path: "puppet-enterprise.png", darkPath: "puppet-enterprise.png")
	}

	@Override
	String getTitle() {
		return "Drift Detection"
	}


	@Override
	String getCategory() {
		return "maintenance"
	}

	@Override
	String getDescription() {
		return "Upgrade the agent to the latest version"
	}

	@Override
	ServiceResponse<AccountDiscovery> execute(AccountDiscovery accountDiscovery) {
		log.info("Trigger Puppet Agent Execution to remediate")
		log.info(accountDiscovery.refType)
		log.info("${accountDiscovery.refId}")

		return ServiceResponse.success(accountDiscovery)
	}

	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}
}