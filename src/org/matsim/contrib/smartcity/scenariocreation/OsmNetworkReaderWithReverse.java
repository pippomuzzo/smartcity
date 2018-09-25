/**
 * 
 */
package org.matsim.contrib.smartcity.scenariocreation;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.io.OsmNetworkReader;

/**
 * Reader that add the direction attribute on links
 * @author Filippo Muzzini
 *
 */
public class OsmNetworkReaderWithReverse extends OsmNetworkReader {

	static final String DIRECTION_ATT = "direction";
	static final String FORWARD = "forward";
	static final String BACKWARD = "backward";

	/**
	 * @param network
	 * @param transformation
	 * @param useHighwayDefaults
	 * @param useVspAdjustments
	 */
	public OsmNetworkReaderWithReverse(Network network, CoordinateTransformation transformation,
			boolean useHighwayDefaults, boolean useVspAdjustments) {
		super(network, transformation, useHighwayDefaults, useVspAdjustments);
	}
	
	@Override
	protected void setOrModifyLinkAttributes(Link l, OsmWay way, boolean forwardDirection) {
		String direction = forwardDirection ? FORWARD : BACKWARD;
		l.getAttributes().putAttribute(DIRECTION_ATT, direction);
	}

}
