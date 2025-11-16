package CloudSim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

public class SJFDatacenterBroker extends DatacenterBroker {
	public SJFDatacenterBroker(String name) throws Exception {
		super(name);
	}
	
	@Override
	protected void processCloudletReturn(SimEvent ev) {
		Cloudlet cloudlet = (Cloudlet) ev.getData();
		
		getCloudletReceivedList().add(cloudlet);
		
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId() + " received");
		cloudletsSubmitted--;
		
		if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
			cloudletExecution(cloudlet);
		}
	}
	protected void cloudletExecution(Cloudlet cloudlet) {
		if (getCloudletList().isEmpty() && cloudletsSubmitted == 0) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": All Cloudlets executed. Finishing...");
			clearDatacenters();
			finishExecution();
		}
	}
	@Override
	protected void processResourceCharacteristics(SimEvent ev) {
		DatacenterCharacteristics characteristics = (DatacenterCharacteristics) ev.getData();
		getDatacenterCharacteristicsList().put(characteristics.getId(), characteristics);
		
		if (getDatacenterCharacteristicsList().size() == getDatacenterIdsList().size()) {
			distributeRequestsForNewVmsAcrossDatacenters();
		}
	}
	protected void distributeRequestsForNewVmsAcrossDatacenters() {
		int datacenterId = getDatacenterIdsList().get(0);
		for (Vm vm : getVmList()) {
			if (!getVmsToDatacentersMap().containsKey(vm.getId())) {
				Log.printLine(CloudSim.clock() + ": " + getName() + ": Creating VM #" + vm.getId() + " in " + CloudSim.getEntityName(datacenterId));
				
				sendNow(datacenterId, CloudSimTags.VM_CREATE_ACK, vm);
				setVmsRequested(1);
				setVmsAcks(0);
			}
		}
	}
}