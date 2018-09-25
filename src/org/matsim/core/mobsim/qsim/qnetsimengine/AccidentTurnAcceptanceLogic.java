/**
 * 
 */
package org.matsim.core.mobsim.qsim.qnetsimengine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.signals.model.SignalSystemsManager;
import org.matsim.contrib.smartcity.accident.Bizzantine;
import org.matsim.contrib.smartcity.accident.BizzantineRedSignal;
import org.matsim.contrib.smartcity.accident.CarAccidentEvent;
import org.matsim.contrib.smartcity.accident.PriorityBizzantine;
import org.matsim.contrib.smartcity.agent.SmartAgentFactory;
import org.matsim.contrib.smartcity.agent.SmartDriverLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.interfaces.SignalizeableItem;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.apache.commons.math3.distribution.BinomialDistribution;

/**
 * Class that determinate if an agent can travers the link (using delegates).
 * In case of bizzantine agents determinate if there is a car accident
 * @author Filippo Muzzini
 *
 */
public class AccidentTurnAcceptanceLogic implements TurnAcceptanceLogic {
	
	/**
	 * Bizzantine Type
	 * @author Filippo Muzzini
	 *
	 */
	public enum BizzantineType {
		Signal, Priority
	}

	private static final double RED_INCIDENT_PROB = 0.53;

	private static final double PRIORITY_INCIDENT_PROB = 1;

	private static final String RED_TYPE = "RedSignal";

	private static final String PRIORITY_TYPE = "Priority";

	private TurnAcceptanceLogic signalDelegate = new SignalWithRightTurnAcceptanceLogic();
	private TurnAcceptanceLogic priorityDelegate = new PriorityTurnAcceptanceLogic();
	private TurnAcceptanceLogic basicDelegate = new DefaultTurnAcceptanceLogic();
	private TurnAcceptanceLogic restrictionDelegate = new RestrictionTurnAcceptanceLogic();
	
	@Inject private Scenario scenario;
	@Inject private EventsManager events;
	@Inject private Injector inj;
	
	private Set<Id<Link>> signalsSet;
	
	@Inject
	public AccidentTurnAcceptanceLogic(SignalSystemsManager signalManager) {
		this.signalsSet = signalManager.getSignalSystems().values().stream().
				flatMap(ss -> ss.getSignals().values().stream()).map(s -> s.getLinkId()).collect(Collectors.toSet());
				
	}
	

	/* (non-Javadoc)
	 * @see org.matsim.core.mobsim.qsim.qnetsimengine.TurnAcceptanceLogic#isAcceptingTurn(org.matsim.api.core.v01.network.Link, org.matsim.core.mobsim.qsim.qnetsimengine.QLaneI, org.matsim.api.core.v01.Id, org.matsim.core.mobsim.qsim.qnetsimengine.QVehicle, org.matsim.core.mobsim.qsim.qnetsimengine.QNetwork)
	 */
	@Override
	public AcceptTurn isAcceptingTurn(Link currentLink, QLaneI currentLane, Id<Link> nextLinkId, QVehicle veh,
			QNetwork qNetwork) {
		AcceptTurn basicTurn = basicDelegate.isAcceptingTurn(currentLink, currentLane, nextLinkId, veh, qNetwork);
		if (basicTurn.equals(AcceptTurn.ABORT)) {
			return basicTurn;
		}
		
		AcceptTurn restrictionTurn = restrictionDelegate.isAcceptingTurn(currentLink, currentLane, nextLinkId, veh, qNetwork);
		if (restrictionTurn.equals(AcceptTurn.ABORT)) {
			return restrictionTurn;
		}
		
		AcceptTurn turn;
		BizzantineType bizzantineType;
		if (signalsSet.contains(currentLink.getId())) {
			turn = signalDelegate.isAcceptingTurn(currentLink, currentLane, nextLinkId, veh, qNetwork);
			bizzantineType = BizzantineType.Signal;
		} else {
			turn = priorityDelegate.isAcceptingTurn(currentLink, currentLane, nextLinkId, veh, qNetwork);
			bizzantineType = BizzantineType.Priority;
		}
		
		if (turn.equals(AcceptTurn.ABORT) || turn.equals(AcceptTurn.GO)) {
			return turn;
		}
		
		//turn is WAIT so consider bizzantine
		Person person = scenario.getPopulation().getPersons().get(veh.getDriver().getId());
		SmartDriverLogic logic = (SmartDriverLogic) person.getCustomAttributes().get(SmartAgentFactory.DRIVERLOGICATT);
		if (!(logic instanceof Bizzantine)) {
			return turn;
		}
		boolean carAccident = false;
		if (bizzantineType == BizzantineType.Signal && logic instanceof BizzantineRedSignal) {
			BizzantineRedSignal bizzantine = (BizzantineRedSignal) logic;
			if (bizzantine.transitOnRed()) {
				carAccident = transitOnRed(currentLink, nextLinkId, veh, qNetwork);
			}
		}
		
		if (bizzantineType == BizzantineType.Priority && logic instanceof PriorityBizzantine) {
			PriorityBizzantine bizzantine = (PriorityBizzantine) logic;
			if (bizzantine.transitWithOutPriority()) {
				carAccident = transitWithoutPriority(currentLink, nextLinkId, veh, qNetwork);
			}
		}
		
		if (carAccident) {
				return AcceptTurn.ABORT;
		}
		
		return turn;
	}

	/**
	 * @return
	 */
	private boolean transitWithoutPriority(Link currentLink, Id<Link> nextLinkId, QVehicle veh, QNetwork qNetwork) {
		Node node = currentLink.getToNode();
		Set<QLaneI> qLanes = getAllLanes(node, currentLink, qNetwork);
		boolean carAccident = isIncidentWithProb(qLanes, PRIORITY_INCIDENT_PROB);
		
		if (carAccident) {
			double time = ((QSim)inj.getInstance(Mobsim.class)).getSimTimer().getTimeOfDay();
			carAccident(currentLink, nextLinkId, veh, null, PRIORITY_TYPE, time);
		}
		
		return carAccident;
	}

	/**
	 * @param currentLink
	 * @param qNetwork
	 * @return
	 */
	private Set<QLaneI> getAllLanes(Node node, Link currentLink, QNetwork qNetwork) {
		Set<? extends Link> inLinks = node.getInLinks().values().stream().filter(l -> !l.equals(currentLink)).collect(Collectors.toSet());
		Set<QLaneI> res = new HashSet<QLaneI>();
		for (Link link : inLinks) {
			QLinkI qLink = qNetwork.getNetsimLink(link.getId());
			List<QLaneI> qLanes = qLink.getOfferingQLanes();
			for (QLaneI qLane : qLanes) {
				res.add(qLane);
			}
		}
		return res;
	}

	/**
	 * @param currentLink
	 * @param currentLane
	 * @param veh
	 */
	private boolean transitOnRed(Link currentLink, Id<Link> nextLinkId, QVehicle veh, QNetwork qNetwork) {
		Node node = currentLink.getToNode();
		Set<QLaneI> qLanes = getGreenQLane(node, currentLink, qNetwork);
		
		//calc accident's probability
		boolean carAccident = isIncidentWithProb(qLanes, RED_INCIDENT_PROB);
		
		if (carAccident) {
			double time = ((QSim)inj.getInstance(Mobsim.class)).getSimTimer().getTimeOfDay();
			carAccident(currentLink, nextLinkId, veh, null, RED_TYPE, time);
		}
		
		return carAccident;
	}
	
	private double calcProbability(Set<QLaneI> qLanes, double prob) {
		double transitVeh = qLanes.stream().mapToLong(l -> l.getAllVehicles().size()).sum();
		double capacity = qLanes.stream().mapToDouble(l -> l.getStorageCapacity()).sum();
		BinomialDistribution dist = new BinomialDistribution((int)capacity, prob);
		return dist.cumulativeProbability((int)transitVeh);
	}
	
	private boolean isIncidentWithProb(Set<QLaneI> qLanes, double prob) {
		double threadshold = calcProbability(qLanes, prob);
		double r = MatsimRandom.getRandom().nextDouble();
		return r <= threadshold;
	}

	/**
	 * @param currentLink
	 * @param nextLink
	 * @param veh
	 */
	private void carAccident(Link currentLink, Id<Link> nextLinkId, QVehicle veh, List<QVehicle> othersVeh, String type, double time ) {
		Id<Link> fromId = currentLink.getId();
		Id<Link> toId = nextLinkId;
		Id<Person> driver = veh.getDriver().getId();
		List<Id<Person>> others = othersVeh.stream().map(v -> v.getDriver().getId()).collect(Collectors.toList());
		CarAccidentEvent event = new CarAccidentEvent(type, fromId, toId, driver, others, time);
		events.processEvent(event);
	}

	/**
	 * Return set of lanes with green signal for node (except the currentLink)
	 * 
	 * @param node
	 * @param currentLink 
	 * @param qNetwork 
	 * @return 
	 */
	protected static Set<QLaneI> getGreenQLane(Node node, Link currentLink, QNetwork qNetwork) {
		Set<? extends Link> inLinks = node.getInLinks().values().stream().filter(l -> !l.equals(currentLink)).collect(Collectors.toSet());
		Set<QLaneI> res = new HashSet<QLaneI>();
		for (Link link : inLinks) {
			QLinkI qLink = qNetwork.getNetsimLink(link.getId());
			List<QLaneI> qLanes = qLink.getOfferingQLanes();
			for (QLaneI qLane : qLanes) {
				if (!(qLane instanceof SignalizeableItem)) {
					res.add(qLane);
				} else {
					//is SignalizeableItem
					SignalizeableItem item = (SignalizeableItem) qLane;
					if (item.hasGreenForAllToLinks()) {
						res.add(qLane);
					}
				}
			}
		}
		return res;
	}

}