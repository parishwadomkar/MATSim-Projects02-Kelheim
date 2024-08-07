package org.matsim.analysis.emissions;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.OsmHbefaMapping;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleUtils;

public class SimpleEmissionsScript {

	private static final String HBEFA_2020_PATH = "https://svn.vsp.tu-berlin.de/repos/public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/";
	private static final String HBEFA_FILE_COLD_DETAILED = HBEFA_2020_PATH + "82t7b02rc0rji2kmsahfwp933u2rfjlkhfpi2u9r20.enc";
	private static final String HBEFA_FILE_WARM_DETAILED = HBEFA_2020_PATH + "944637571c833ddcf1d0dfcccb59838509f397e6.enc";
	private static final String HBEFA_FILE_COLD_AVERAGE = HBEFA_2020_PATH + "r9230ru2n209r30u2fn0c9rn20n2rujkhkjhoewt84202.enc" ;
	private static final String HBEFA_FILE_WARM_AVERAGE = HBEFA_2020_PATH + "7eff8f308633df1b8ac4d06d05180dd0c5fdf577.enc";

	public static void main(String[] args) {

		// set files
		var config = ConfigUtils.createConfig();
		config.vehicles().setVehiclesFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/output/1pct/kelheim-v3.0-1pct.output_vehicles.xml.gz");
		config.network().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/output/1pct/kelheim-v3.0-1pct.output_network.xml.gz");
		config.transit().setTransitScheduleFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/output/1pct/kelheim-v3.0-1pct.output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/output/1pct/kelheim-v3.0-1pct.output_transitVehicles.xml.gz");
		config.plans().setInputFile(null);
		config.global().setCoordinateSystem("EPSG:25832");
		config.eventsManager().setNumberOfThreads(null);
		config.eventsManager().setEstimatedNumberOfEvents(null);
		config.global().setNumberOfThreads(1);

		// configure emission contrib
		var emissionsConfigGroup = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
		emissionsConfigGroup.setDetailedColdEmissionFactorsFile(HBEFA_FILE_COLD_DETAILED);
		emissionsConfigGroup.setDetailedWarmEmissionFactorsFile(HBEFA_FILE_WARM_DETAILED);
		emissionsConfigGroup.setAverageColdEmissionFactorsFile(HBEFA_FILE_COLD_AVERAGE);
		emissionsConfigGroup.setAverageWarmEmissionFactorsFile(HBEFA_FILE_WARM_AVERAGE);
		emissionsConfigGroup.setWritingEmissionsEvents(true);
		emissionsConfigGroup.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageThenAverageTable);
		emissionsConfigGroup.setHbefaTableConsistencyCheckingLevel(EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.consistent);

		// load files
		var scenario = ScenarioUtils.loadScenario(config);

		// add hbefa mapping for road types
		var mapping = OsmHbefaMapping.build();
		var network = scenario.getNetwork();

		for (Link link : scenario.getNetwork().getLinks().values()) {
			//pt links can be disregarded
			if (!link.getAllowedModes().contains("pt")) {
				NetworkUtils.setType(link, NetworkUtils.getType(link).replaceFirst("highway.", ""));
			}
		}

		mapping.addHbefaMappings(network);

		// prepare Vehicle properties
		for (var type : scenario.getVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();
			VehicleUtils.setHbefaTechnology(engineInformation, "average");
			VehicleUtils.setHbefaSizeClass(engineInformation, "average");
			VehicleUtils.setHbefaEmissionsConcept(engineInformation, "average");

			if (type.getId().toString().equals(TransportMode.car)) {
				VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
			} else if (type.getId().toString().equals("freight")) {
				VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
			}
			else {
				VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
			}
		}

		for (var type : scenario.getTransitVehicles().getVehicleTypes().values()) {
			var engineInformation = type.getEngineInformation();
			VehicleUtils.setHbefaTechnology(engineInformation, "average");
			VehicleUtils.setHbefaSizeClass(engineInformation, "average");
			VehicleUtils.setHbefaEmissionsConcept(engineInformation, "average");
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
		}

		// process
		var manager = EventsUtils.createEventsManager();
		var emissionModule = new EmissionModule(scenario, manager);
		var writer = new EventWriterXML("/Users/janek/Downloads/output_events.xml.gz");
		manager.initProcessing();
		manager.addHandler(writer);
		EventsUtils.readEvents(manager, "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/output/1pct/kelheim-v3.0-1pct.output_events.xml.gz");
		manager.finishProcessing();
	}
}
