/*
* Copyright 2022 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.Plugin

class PuppetEnterprisePlugin extends Plugin {

    @Override
    String getCode() {
        return 'morpheus-puppet-enterprise-plugin'
    }

    @Override
    void initialize() {
        this.setName("Puppet Enterprise Plugin")
        this.registerProvider(new PuppetEnterpriseIntegrationProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseInstanceTabProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseReportProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseTagNodeTaskProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseBoltTaskProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseBoltPlanTaskProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseGuidanceProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseAnalyticsProvider(this,this.morpheus))
        this.registerProvider(new PuppetEnterpriseOptionSourceProvider(this,this.morpheus))
    }

    /**
     * Called when a plugin is being removed from the plugin manager (aka Uninstalled)
     */
    @Override
    void onDestroy() {
        //nothing to do for now
    }
}
