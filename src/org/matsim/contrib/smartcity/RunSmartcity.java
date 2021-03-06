/**
 * 
 */
package org.matsim.contrib.smartcity;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.otfvis.OTFVisWithSignalsLiveModule;
import org.matsim.contrib.smartcity.accident.AccidentModule;
import org.matsim.contrib.smartcity.actuation.semaphore.SmartSemaphoreModule;
import org.matsim.contrib.smartcity.agent.SmartAgentModule;
import org.matsim.contrib.smartcity.analisys.AnalisysModule;
import org.matsim.contrib.smartcity.comunication.ComunicationModule;
import org.matsim.contrib.smartcity.perception.SmartPerceptionModule;
import org.matsim.contrib.smartcity.restriction.RestrictionsModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

/**
 * @author Filippo Muzzini
 *
 */
public class RunSmartcity {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Gbl.assertIf(args.length >=1 && args[0]!="" );
		run(ConfigUtils.loadConfig(args[0]));
	}

	/**
	 * @param loadConfig
	 */
	private static void run(Config config) {
		//get a simple scenario		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;
				
		Controler controler = new Controler(scenario) ;
		
		//add perception module
		controler.addOverridingModule(new SmartPerceptionModule());
		
		//add signal module
		controler.addOverridingModule(new SmartSemaphoreModule());
		
		//add smartagent module
		controler.addOverridingModule(new SmartAgentModule());
		
		//add comunication module
		controler.addOverridingModule(new ComunicationModule());
		
		//add accident module
		controler.addOverridingModule(new AccidentModule());
		
		//add restriction module
		controler.addOverridingModule(new RestrictionsModule());
		
		controler.addOverridingModule(new AnalisysModule());
		
		//add vis module
		if (config.getModules().containsKey(OTFVisConfigGroup.GROUP_NAME)) {
			ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.class);
			
			//controler.addOverridingModule(new OTFVisLiveModule());
			/*controler.addOverridingModule(new AbstractModule() {
				
				@Override
				public void install() {
					ParkingSlotVisualiser visualiser = new ParkingSlotVisualiser(scenario);
					addEventHandlerBinding().toInstance(visualiser);
					addControlerListenerBinding().toInstance(visualiser);
				}
			});*/
			
			controler.addOverridingModule(new OTFVisWithSignalsLiveModule());
		}
		
		controler.run();
		
	}

}
