package CloudSim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.List;

public class SJFDatacenterBroker extends DatacenterBroker {

    public SJFDatacenterBroker(String name) throws Exception {
        super(name);
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);

        Log.printLine(CloudSim.clock() + ": " + getName()
                + ": Cloudlet " + cloudlet.getCloudletId() + " received");

        cloudletsSubmitted--;

        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            cloudletExecution(cloudlet);
        }
    }

    protected void cloudletExecution(Cloudlet cloudlet) {
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            Log.printLine(CloudSim.clock() + ": " + getName()
                    + ": All Cloudlets executed. Finishing...");

            clearDatacenters();
            finishExecution();
        } else {
            if (getCloudletList().size() > 0 && cloudletsSubmitted == 0) {
                clearDatacenters();
                createVmsInDatacenter(0);
            }
        }
    }

    @Override
    protected void processResourceCharacteristics(SimEvent ev) {
        DatacenterCharacteristics characteristics =
                (DatacenterCharacteristics) ev.getData();

        getDatacenterCharacteristicsList()
                .put(characteristics.getId(), characteristics);

        if (getDatacenterCharacteristicsList().size()
                == getDatacenterIdsList().size()) {

            distributeRequestsForNewVmsAcrossDatacenters();
        }
    }

    /**
     * Multiple VM distribution — round-robin across datacenters.
     */
    protected void distributeRequestsForNewVmsAcrossDatacenters() {
        int numberOfVmsAllocated = 0;
        int i = 0;

        final List<Integer> availableDatacenters = getDatacenterIdsList();

        for (Vm vm : getVmList()) {

            int datacenterId =
                    availableDatacenters.get(i++ % availableDatacenters.size());
            String datacenterName = CloudSim.getEntityName(datacenterId);

            if (!getVmsToDatacentersMap().containsKey(vm.getId())) {

                Log.printLine(CloudSim.clock() + ": " + getName()
                        + ": Trying to Create VM #" + vm.getId()
                        + " in " + datacenterName);

                sendNow(datacenterId, CloudSimTags.VM_CREATE_ACK, vm);
                numberOfVmsAllocated++;
            }
        }

        setVmsRequested(numberOfVmsAllocated);
        setVmsAcks(0);
    }
}
