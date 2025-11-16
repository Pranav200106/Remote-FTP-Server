package CloudSim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

public class SJF_Scheduler {

    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmList;
    private static Datacenter[] datacenter;

    private static double[][] commMatrix;
    private static double[][] execMatrix;

    /* ========================= VM CREATION ========================== */

    private static List<Vm> createVM(int userId, int vms) {
        LinkedList<Vm> list = new LinkedList<>();

        long size = 10000;
        int ram = 512;
        int mips = 250;
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        Vm[] vm = new Vm[vms];
        for (int i = 0; i < vms; i++) {
            vm[i] = new Vm(
                    i, userId, mips, pesNumber, ram, bw, size, vmm,
                    new CloudletSchedulerSpaceShared()
            );
            list.add(vm[i]);
        }
        return list;
    }

    /* ======================= CLOUDLET CREATION ====================== */

    private static List<Cloudlet> createCloudlet(int userId, int cloudlets, int idShift) {
        LinkedList<Cloudlet> list = new LinkedList<>();

        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;

        UtilizationModel utilizationModel = new UtilizationModelFull();
        Cloudlet[] cloudlet = new Cloudlet[cloudlets];

        for (int i = 0; i < cloudlets; i++) {

            int dcId = (int) (Math.random() * Constants.NO_OF_DATA_CENTERS);
            long length = (long) (1e3 * (commMatrix[i][dcId] + execMatrix[i][dcId]));

            cloudlet[i] = new Cloudlet(
                    idShift + i,
                    length,
                    pesNumber,
                    fileSize,
                    outputSize,
                    utilizationModel,
                    utilizationModel,
                    utilizationModel
            );

            cloudlet[i].setUserId(userId);
            list.add(cloudlet[i]);
        }

        // Sort SJF
        list.sort(Comparator.comparingLong(Cloudlet::getCloudletLength));

        return list;
    }

    /* ============================= MAIN ============================== */

    public static void main(String[] args) {

        Log.printLine("Starting SJF Scheduler...");

        new GenerateMatrices();
        execMatrix = GenerateMatrices.getExecMatrix();
        commMatrix = GenerateMatrices.getCommMatrix();

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            datacenter = new Datacenter[Constants.NO_OF_DATA_CENTERS];

            for (int i = 0; i < Constants.NO_OF_DATA_CENTERS; i++) {
                datacenter[i] =
                        DatacenterCreator.createDatacenter("Datacenter_" + i);
            }

            DatacenterBroker broker = createBroker("Broker_0");
            int brokerId = broker.getId();

            vmList = createVM(brokerId, Constants.NO_OF_DATA_CENTERS);
            cloudletList = createCloudlet(brokerId, Constants.NO_OF_TASKS, 0);

            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);

            CloudSim.startSimulation();

            List<Cloudlet> newList = broker.getCloudletReceivedList();

            CloudSim.stopSimulation();

            printCloudletList(newList);

            Log.printLine(SJF_Scheduler.class.getName() + " finished!");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    /* =================== BROKER CREATION =================== */

    private static SJFDatacenterBroker createBroker(String name) throws Exception {
        return new SJFDatacenterBroker(name);
    }

    /* ====================== PRINT RESULTS ===================== */

    private static void printCloudletList(List<Cloudlet> list) {
        DecimalFormat dft = new DecimalFormat("###.##");
        dft.setMinimumIntegerDigits(2);

        String logFile = "runtime_log.txt";

        try (FileWriter fw = new FileWriter(logFile, false);
             PrintWriter pw = new PrintWriter(fw)) {

            String header = String.format(
                    "%-12s %-10s %-15s %-8s %-12s %-12s %-12s %-12s",
                    "CloudletID", "STATUS", "DatacenterID", "VMID",
                    "ExecTime", "StartTime", "FinishTime", "WaitingTime"
            );

            Log.printLine();
            Log.printLine("========== OUTPUT ==========");
            Log.printLine(header);

            pw.println("========== OUTPUT ==========");
            pw.println(header);

            for (Cloudlet cloudlet : list) {

                double execTime = cloudlet.getActualCPUTime();
                String status = (execTime > 2000) ? "ANOMALY" : "SUCCESS";

                String row = String.format(
                        "%-12d %-10s %-15d %-8d %-12s %-12s %-12s %-12s",
                        cloudlet.getCloudletId(),
                        status,
                        cloudlet.getResourceId(),
                        cloudlet.getVmId(),
                        dft.format(execTime),
                        dft.format(cloudlet.getExecStartTime()),
                        dft.format(cloudlet.getFinishTime()),
                        dft.format(cloudlet.getWaitingTime())
                );

                Log.printLine(row);
                pw.println(row);
            }

            Log.printLine("Anomaly detection results saved to " + logFile);
            pw.println();
            pw.println("Anomaly detection results saved to " + logFile);

        } catch (IOException e) {
            e.printStackTrace();
        }

        double makespan = calcMakespan(list);
        Log.printLine("Makespan using SJF: " + makespan);
    }

    /* ======================= MAKESPAN ======================== */

    private static double calcMakespan(List<Cloudlet> list) {

        double makespan = 0;
        double[] dcWorkingTime = new double[Constants.NO_OF_DATA_CENTERS];

        for (int i = 0; i < Constants.NO_OF_TASKS; i++) {

            int dcId = list.get(i).getVmId() % Constants.NO_OF_DATA_CENTERS;

            if (dcWorkingTime[dcId] != 0)
                --dcWorkingTime[dcId];

            dcWorkingTime[dcId] += execMatrix[i][dcId] + commMatrix[i][dcId];

            makespan = Math.max(makespan, dcWorkingTime[dcId]);
        }
        return makespan;
    }
}
