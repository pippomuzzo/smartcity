/**
 * 
 */
package org.matsim.contrib.smartcity.comunication;

import java.util.Iterator;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.smartcity.agent.StaticDriverLogic;
import org.matsim.contrib.smartcity.comunication.wrapper.ComunicationWrapper;

import com.google.inject.Inject;

/**
 * @author Filippo Muzzini
 *
 */
public class ProvaStaticV2VDriverLogic extends StaticDriverLogic implements ComunicationClient, ComunicationServer {

	@Inject ComunicationWrapper wrapper;
	
	/* (non-Javadoc)
	 * @see org.matsim.contrib.smartcity.comunication.ComunicationClient#discover()
	 */
	@Override
	public Set<ComunicationServer> discover() {
		return wrapper.discover(this.actualLink);
	}

	/* (non-Javadoc)
	 * @see org.matsim.contrib.smartcity.comunication.ComunicationClient#sendToMe(org.matsim.contrib.smartcity.comunication.ComunicationMessage)
	 */
	@Override
	public void sendToMe(ComunicationMessage message) {
		System.out.println("sono "+this.toString()+" ho ricevuto da "+message.getSender());
	}
	
	@Override
	public void setActualLink(Id<Link> actualLink) {
		super.setActualLink(actualLink);
		Iterator<ComunicationServer> serverIterator = this.discover().iterator();
		while (serverIterator.hasNext()) {
			ComunicationServer server = serverIterator.next();
			if (!server.equals(this)) {
				server.sendToMe(new ComunicationMessage(this));
				break;
			}
		}
		
	}

}
