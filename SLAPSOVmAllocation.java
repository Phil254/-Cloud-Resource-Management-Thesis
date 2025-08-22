package v5_SLAPSOVmAllocation;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.Storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat; // Added for Timestamp
import java.util.*;

/**
 * SLAPSOVmAllocation is a Java class that implements an SLA-based Particle Swarm Optimization (PSO)
 * algorithm for Virtual Machine (VM) allocation in a cloud computing environment.
 * It integrates with the CloudSim toolkit to simulate the allocation process and evaluate performance
 * metrics such such as Makespan, SLA violations, and host utilization.
 *
 * IMPORTANT CHANGE: The 'main' method now accepts simulation parameters via command-line arguments.
 */
public class SLAPSOVmAllocation {

    // --- Global Configuration for Comparison Log ---
    private static final String COMPARISON_LOG_FILENAME = "compare_sla_results.csv"; // Name of the comparison log file.

    /* -------------------------------------
     * Utility: Generate Unique Filename
     * ------------------------------------- */

    /**
     * Generates a unique filename by appending a counter if the base filename already exists.
     * This prevents overwriting existing simulation output files.
     *
     * @param baseFilename The desired base filename (e.g., "results.csv").
     * @return A unique filename (e.g., "results.csv", "results_1.csv", "results_2.csv").
     */
    private static String getUniqueFilename(String baseFilename) {
        File file = new File(baseFilename);
        if (!file.exists()) {
            return baseFilename; // If file doesn't exist, the base filename is unique.
        }

        int counter = 1;
        int dotIndex = baseFilename.lastIndexOf('.'); // Find the last dot to separate base and extension.
        String base, extension;

        if (dotIndex != -1) {
            base = baseFilename.substring(0, dotIndex); // Base part of the filename (without extension).
            extension = baseFilename.substring(dotIndex); // Extension part (including the dot).
        } else {
            base = baseFilename; // No extension found.
            extension = "";      // Empty extension.
        }

        // Loop until a non-existent filename is found.
        while (true) {
            String newFilename = base + "_" + counter + extension; // Append counter to create a new filename.
            File newFile = new File(newFilename);
            if (!newFile.exists()) {
                return newFilename; // Found a unique filename.
            }
            counter++; // Increment counter for the next attempt.
        }
    }

    /* -----------------------------
     * Inner Helper Classes
     * ----------------------------- */

    /**
     * VmData class represents the characteristics of a Virtual Machine (VM).
     * It stores parameters essential for VM allocation and simulation.
     */
    static class VmData {
        int vmId;           // Unique identifier for the VM.
        int mips;           // Millions Instructions Per Second (MIPS) requirement of the VM.
        int ram;            // RAM (in MB) requirement of the VM.
        int bw;             // Bandwidth (in Mbps) requirement of the VM.
        int size;           // Storage size (in MB) requirement of the VM.
        long cloudletLength; // Length of the Cloudlet (task) to be executed on this VM (in MIPS).
        double deadline;    // Deadline for the Cloudlet's execution on this VM (in seconds).

        /**
         * Constructor for VmData.
         * @param vmId Unique VM ID.
         * @param mips MIPS requirement.
         * @param ram RAM requirement.
         * @param bw Bandwidth requirement.
         * @param size Storage size.
         * @param cloudletLength Length of the associated cloudlet.
         * @param deadline Deadline for the associated cloudlet.
         */
        public VmData(int vmId, int mips, int ram, int bw, int size, long cloudletLength, double deadline) {
            this.vmId = vmId;
            this.mips = mips;
            this.ram = ram;
            this.bw = bw;
            this.size = size;
            this.cloudletLength = cloudletLength;
            this.deadline = deadline;
        }
    }

    /**
     * Particle class represents a candidate solution in the Particle Swarm Optimization (PSO) algorithm.
     * Each particle holds a potential allocation of VMs to hosts.
     */
    static class Particle {
        int[] allocation;     // An array where allocation[i] is the index of the host assigned to VM 'i'.
        double fitness;       // The current fitness value of this particle's allocation. Lower is better.
        int[] bestAllocation; // The personal best allocation found by this particle so far.
        double bestFitness;   // The fitness value of the personal best allocation.

        /**
         * Constructor for Particle. Initializes the particle with a random allocation.
         * @param numVMs The total number of VMs to allocate.
         * @param numHosts The total number of available hosts.
         * @param rand A Random object for generating initial random allocations.
         */
        public Particle(int numVMs, int numHosts, Random rand) {
            allocation = new int[numVMs];     // Initialize current allocation array.
            bestAllocation = new int[numVMs]; // Initialize personal best allocation array.

            // Assign each VM to a random host initially.
            for (int i = 0; i < numVMs; i++) {
                allocation[i] = rand.nextInt(numHosts); // Random host index [0, numHosts-1].
                bestAllocation[i] = allocation[i];      // Personal best starts as current allocation.
            }

            fitness = Double.MAX_VALUE;       // Initialize current fitness to a very high value (minimization).
            bestFitness = Double.MAX_VALUE;   // Initialize personal best fitness to a very high value.
        }

        /**
         * Creates and returns a deep copy of this Particle object.
         * This is crucial for maintaining distinct particle states (e.g., global best).
         * @return A new Particle object that is a deep copy of the current particle.
         */
        public Particle cloneParticle() {
            // Create a new Particle, only using its constructor to initialize array sizes.
            // The '1' for numHosts in the constructor is a placeholder as we'll overwrite values.
            Particle clone = new Particle(allocation.length, 1, new Random());

            // Deep copy the allocation arrays.
            clone.allocation = this.allocation.clone();
            clone.bestAllocation = this.bestAllocation.clone();

            // Copy the fitness values.
            clone.fitness = this.fitness;
            clone.bestFitness = this.bestFitness;
            return clone;
        }
    }

    /**
     * HostCapacity class represents the effective resource capacities of a physical host.
     */
    static class HostCapacity {
        double mips; // Total MIPS capacity of the host.
        double ram;  // Total RAM capacity (in MB) of the host.
        double bw;   // Total Bandwidth capacity (in Mbps) of the host.

        /**
         * Constructor for HostCapacity.
         * @param mips MIPS capacity.
         * @param ram RAM capacity.
         * @param bw Bandwidth capacity.
         */
        public HostCapacity(double mips, double ram, double bw) {
            this.mips = mips;
            this.ram = ram;
            this.bw = bw;
        }
    }

    /* -------------------------------------
     * VM Generation & CSV Export (Updated)
     * ------------------------------------- */

    /**
     * Generates a list of VmData with moderate ranges:
     * - MIPS: [1000, 3000]
     * - RAM: [1024, 8192]
     * - BW: [500, 3000]
     * - 30% chance for high-demand: cloudletLength in [4000, 10000], deadline in [8, 10] sec.
     * - Otherwise: cloudletLength in [1000, 4000], deadline in [15, 25] sec.
     */
    static List<VmData> generateVmDataList(int numVMs) {
        List<VmData> vmList = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < numVMs; i++) {
            int mips = 1000 + rand.nextInt(2001);  // 1000 to 3000
            int ram = 1024 + rand.nextInt(7169);     // 1024 to 8192
            int bw = 500 + rand.nextInt(2501);         // 500 to 3000
            int size = 10000;
            long cloudletLength;
            double deadline;
            if (rand.nextDouble() < 0.3) {
                // High-demand VM.
                cloudletLength = 4000 + rand.nextInt(6001); // 4000 to 10000
                deadline = 8 + rand.nextDouble() * 2;         // 8 to 10 sec.
            } else {
                // Lenient VM.
                cloudletLength = 1000 + rand.nextInt(3001); // 1000 to 4000
                deadline = 15 + rand.nextDouble() * 10;       // 15 to 25 sec.
            }
            vmList.add(new VmData(i, mips, ram, bw, size, cloudletLength, deadline));
        }
        return vmList;
    }

    /**
     * Writes the generated VM characteristics to a CSV file.
     * Uses overwrite mode for specific VM set. If the base filename exists,
     * a unique name is generated.
     */
    static void writeVmDataToCSV(List<VmData> vmList, String baseFilename) {
        String filename = getUniqueFilename(baseFilename);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, false))) { // Changed to false to overwrite for specific VM set
            bw.write("VMID,MIPS,RAM,BW,SIZE,CLOUDLETLENGTH,unused,DEADLINE");
            bw.newLine();
            for (VmData vm : vmList) {
                String line = vm.vmId + "," + vm.mips + "," + vm.ram + "," + vm.bw + "," + vm.size + ","
                        + vm.cloudletLength + ",NA," + String.format("%.2f", vm.deadline);
                bw.write(line);
                bw.newLine();
            }
            bw.flush();
            System.out.println("VM characteristics exported to: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* -------------------------------------
     * PSO Allocation Methods (Updated)
     * ------------------------------------- */

    /**
     * Creates an array of HostCapacity objects.
     * Updated host capacities:
     * - small: [3000, 4096, 3000]
     * - medium: [4000, 8192, 4000]
     * - large: [6000, 16384, 6000]
     * For mixed, one tier is chosen randomly.
     * Uses a reduction factor between 0.9 and 1.0.
     */
    static HostCapacity[] createHostCapacities(int numHosts, int landscape) {
        HostCapacity[] hostCaps = new HostCapacity[numHosts];
        Random rand = new Random();
        for (int i = 0; i < numHosts; i++) {
            double baseMips = 0, baseRam = 0, baseBw = 0;
            if (landscape == 1) {
                baseMips = 3000; baseRam = 4096; baseBw = 3000;
            } else if (landscape == 2) {
                baseMips = 4000; baseRam = 8192; baseBw = 4000;
            } else if (landscape == 3) {
                baseMips = 6000; baseRam = 16384; baseBw = 6000;
            } else if (landscape == 4) {
                int tier = rand.nextInt(3);
                if (tier == 0) { baseMips = 3000; baseRam = 4096; baseBw = 3000; }
                else if (tier == 1) { baseMips = 4000; baseRam = 8192; baseBw = 4000; }
                else { baseMips = 6000; baseRam = 16384; baseBw = 6000; }
            }
            double reductionFactor = 0.9 + (rand.nextDouble() * 0.1);
            hostCaps[i] = new HostCapacity(baseMips * reductionFactor, baseRam * reductionFactor, baseBw * reductionFactor);
        }
        return hostCaps;
    }

    /**
     * Repairs a particle’s allocation if a host is overloaded.
     */
    static void repairParticle(Particle particle, List<VmData> vmList, HostCapacity[] hostCaps) {
        int numHosts = hostCaps.length;
        int numVMs = vmList.size();
        while (true) {
            boolean violationFound = false;
            double[] usedMips = new double[numHosts];
            double[] usedRam = new double[numHosts];
            double[] usedBw = new double[numHosts];
            for (int i = 0; i < numVMs; i++) {
                int hostIndex = particle.allocation[i];
                VmData vm = vmList.get(i);
                usedMips[hostIndex] += vm.mips;
                usedRam[hostIndex] += vm.ram;
                usedBw[hostIndex] += vm.bw;
            }
            for (int i = 0; i < numVMs; i++) {
                int hostIndex = particle.allocation[i];
                VmData vm = vmList.get(i);
                if (usedMips[hostIndex] > hostCaps[hostIndex].mips ||
                    usedRam[hostIndex] > hostCaps[hostIndex].ram ||
                    usedBw[hostIndex] > hostCaps[hostIndex].bw) {

                    boolean reassigned = false;
                    for (int j = 0; j < numHosts; j++) {
                        if (usedMips[j] + vm.mips <= hostCaps[j].mips &&
                            usedRam[j] + vm.ram <= hostCaps[j].ram &&
                            usedBw[j] + vm.bw <= hostCaps[j].bw) {
                            usedMips[hostIndex] -= vm.mips;
                            usedRam[hostIndex] -= vm.ram;
                            usedBw[hostIndex] -= vm.bw;
                            particle.allocation[i] = j;
                            usedMips[j] += vm.mips;
                            usedRam[j] += vm.ram;
                            usedBw[j] += vm.bw;
                            reassigned = true;
                            violationFound = true;
                            break;
                        }
                    }
                    if (!reassigned) {
                        violationFound = false;
                        break;
                    }
                }
            }
            if (!violationFound)
                break;
        }
    }

    /**
     * Computes the fitness of a particle.
     * The fitness consists of:
     * - Makespan (maximum predicted host processing time)
     * - SLA penalty (if predicted execution time exceeds deadline)
     * - Load-balancing penalty (variance in resource utilization)
     */
    static double computeFitness(Particle particle, List<VmData> vmList, HostCapacity[] hostCaps,
                                 double scalingFactor, double slaCostFactor, double lbWeight) {
        int numHosts = hostCaps.length;
        int numVMs = vmList.size();
        double[] hostTime = new double[numHosts];
        double totalSlaPenalty = 0;
        // Using predicted execution time.
        for (int i = 0; i < numVMs; i++) {
            int hostIndex = particle.allocation[i];
            VmData vm = vmList.get(i);
            double execTime = (vm.cloudletLength / hostCaps[hostIndex].mips) * scalingFactor;
            hostTime[hostIndex] += execTime;
            if (execTime > vm.deadline) {
                totalSlaPenalty += slaCostFactor * (execTime - vm.deadline);
            }
        }
        double makespan = 0;
        for (int j = 0; j < numHosts; j++) {
            makespan = Math.max(makespan, hostTime[j]);
        }

        double[] usedMips = new double[numHosts];
        double[] usedRam = new double[numHosts];
        double[] usedBw = new double[numHosts];
        for (int i = 0; i < numVMs; i++) {
            int hostIndex = particle.allocation[i];
            VmData vm = vmList.get(i);
            usedMips[hostIndex] += vm.mips;
            usedRam[hostIndex] += vm.ram;
            usedBw[hostIndex] += vm.bw;
        }
        double[] cpuUtil = new double[numHosts];
        double[] ramUtil = new double[numHosts];
        double[] bwUtil = new double[numHosts];
        for (int j = 0; j < numHosts; j++) {
            cpuUtil[j] = usedMips[j] / hostCaps[j].mips;
            ramUtil[j] = usedRam[j] / hostCaps[j].ram;
            bwUtil[j] = usedBw[j] / hostCaps[j].bw;
        }
        double cpuMean = 0, ramMean = 0, bwMean = 0;
        for (int j = 0; j < numHosts; j++) {
            cpuMean += cpuUtil[j];
            ramMean += ramUtil[j];
            bwMean += bwUtil[j];
        }
        cpuMean /= numHosts;
        ramMean /= numHosts;
        bwMean /= numHosts;
        double cpuVar = 0, ramVar = 0, bwVar = 0;
        for (int j = 0; j < numHosts; j++) {
            cpuVar += Math.pow(cpuUtil[j] - cpuMean, 2);
            ramVar += Math.pow(ramUtil[j] - ramMean, 2);
            bwVar += Math.pow(bwUtil[j] - bwMean, 2);
        }
        cpuVar /= numHosts;
        ramVar /= numHosts;
        bwVar /= numHosts;
        double loadBalancingPenalty = lbWeight * (cpuVar + ramVar + bwVar);
        return makespan + totalSlaPenalty + loadBalancingPenalty;
    }

    /**
     * Runs the PSO optimization algorithm.
     * The PSO coefficients (cognitive, social, inertia) are now tunable parameters.
     */
    static Particle psoOptimization(List<VmData> vmList, HostCapacity[] hostCaps, int numHosts,
                                    int swarmSize, int maxIterations, double scalingFactor,
                                    double slaCostFactor, double lbWeight, // Existing params
                                    double cognitiveCoeff, double socialCoeff, double inertiaWeight) { // New tunable PSO params
        int numVMs = vmList.size();
        Random rand = new Random();
        Particle[] swarm = new Particle[swarmSize];
        for (int i = 0; i < swarmSize; i++) {
            swarm[i] = new Particle(numVMs, numHosts, rand);
            repairParticle(swarm[i], vmList, hostCaps);
            swarm[i].fitness = computeFitness(swarm[i], vmList, hostCaps, scalingFactor, slaCostFactor, lbWeight);
            swarm[i].bestFitness = swarm[i].fitness;
            swarm[i].bestAllocation = swarm[i].allocation.clone();
        }
        Particle globalBest = swarm[0].cloneParticle();
        for (int i = 1; i < swarmSize; i++) {
            if (swarm[i].fitness < globalBest.fitness) {
                globalBest = swarm[i].cloneParticle();
            }
        }
        // PSO coefficients are now passed as arguments.
        for (int iter = 0; iter < maxIterations; iter++) {
            for (int i = 0; i < swarmSize; i++) {
                Particle p = swarm[i];
                for (int j = 0; j < numVMs; j++) {
                    if (rand.nextDouble() < inertiaWeight) {
                        if (p.allocation[j] != globalBest.allocation[j] &&
                            rand.nextDouble() < socialCoeff / (socialCoeff + cognitiveCoeff)) {
                            p.allocation[j] = globalBest.allocation[j];
                        } else if (p.allocation[j] != p.bestAllocation[j] &&
                                   rand.nextDouble() < cognitiveCoeff / (socialCoeff + cognitiveCoeff)) {
                            p.allocation[j] = p.bestAllocation[j];
                        } else {
                            p.allocation[j] = rand.nextInt(numHosts);
                        }
                    }
                }
                repairParticle(p, vmList, hostCaps);
                p.fitness = computeFitness(p, vmList, hostCaps, scalingFactor, slaCostFactor, lbWeight);
                if (p.fitness < p.bestFitness) {
                    p.bestFitness = p.fitness;
                    p.bestAllocation = p.allocation.clone();
                }
                if (p.fitness < globalBest.fitness) {
                    globalBest = p.cloneParticle();
                }
            }
        }
        return globalBest;
    }

    /* -------------------------------------
     * Datacenter and Broker Creation
     * ------------------------------------- */

    /**
     * Creates a Datacenter.
     * Uses the 9-parameter constructor for DatacenterCharacteristics (including VMM).
     */
    private static Datacenter createDatacenter(String name, HostCapacity[] hostCaps) throws Exception {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < hostCaps.length; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(i, new PeProvisionerSimple((int) hostCaps[i].mips)));
            int hostRam = (int) hostCaps[i].ram;
            int hostBw = (int) hostCaps[i].bw;
            int storage = 1000000;
            Host host = new Host(i,
                    new RamProvisionerSimple(hostRam),
                    new BwProvisionerSimple(hostBw),
                    storage,
                    peList,
                    new VmSchedulerTimeShared(peList));
            hostList.add(host);
        }
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen"; // VMM string.
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);
        Datacenter datacenter = new Datacenter(name, characteristics,
                new VmAllocationPolicySimple(hostList), new LinkedList<Storage>(), 0);
        return datacenter;
    }

    /**
     * Creates a DatacenterBroker.
     */
    private static DatacenterBroker createBroker() throws Exception {
        return new DatacenterBroker("Broker");
    }

    /* -------------------------------------
     * NEW: Comparison Log Export Method
     * ------------------------------------- */

    /**
     * Writes relevant performance metrics for comparison across algorithms to a CSV file.
     * This method appends to the file, creating it and writing a header if it doesn't exist.
     * Columns are narrowed down to relevant comparison metrics.
     */
    public static void writeComparisonResultsToCSV(
            String algorithmName, int numVMs, int numHosts, int landscape,
            double scalingFactor, int slaViolations, int failedAllocations,
            double avgResponseTime, double avgTurnaroundTime, double makespan,
            double throughput, double avgHostCpuUtil, double stdDevHostCpuUtil,
            double avgQosIndex) {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(COMPARISON_LOG_FILENAME, true))) {
            // Write header only if the file is new/empty
            File file = new File(COMPARISON_LOG_FILENAME);
            if (file.length() == 0) {
                writer.write("Timestamp,Algorithm,NumVMs,NumHosts,Landscape,ScalingFactor," +
                             "SLA_Violations,Failed_Allocations,Avg_ResponseTime,Avg_TurnaroundTime," +
                             "Makespan,Throughput,Avg_Host_CPU_Util(%),StdDev_Host_CPU_Util(%),Avg_QoS_Index");
                writer.newLine();
            }

            // Write data row
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String line = String.format("%s,%s,%d,%d,%d,%.2f,%d,%d,%.2f,%.2f,%.2f,%.4f,%.2f,%.2f,%.4f",
                    timestamp,
                    algorithmName,
                    numVMs, numHosts, landscape, scalingFactor,
                    slaViolations, failedAllocations, avgResponseTime, avgTurnaroundTime,
                    makespan, throughput, avgHostCpuUtil * 100, stdDevHostCpuUtil * 100,
                    avgQosIndex);
            writer.write(line);
            writer.newLine();
            writer.flush();
            System.out.println("Comparison results logged to: " + COMPARISON_LOG_FILENAME);

        } catch (IOException e) {
            System.err.println("Error writing comparison results to CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /* -------------------------------------
     * Main Simulation and Nicely Padded Output with Unique File Names
     * ------------------------------------- */

    /**
     * Main method to run the SLA-based PSO VM Allocation simulation.
     * It now accepts parameters via command-line arguments.
     *
     * Expected command-line arguments order:
     * args[0]: numVMs (integer) - Number of VMs to generate.
     * args[1]: numHosts (integer) - Number of physical hosts.
     * args[2]: landscape (integer) - Host configuration type (1-4).
     * args[3]: swarmSize (integer) - Number of particles in PSO.
     * args[4]: maxIterations (integer) - Maximum PSO iterations.
     * args[5]: scalingFactor (double) - Factor for cloudlet execution time.
     * args[6]: slaCostFactor (double) - Weighting for SLA violation.
     * args[7]: lbWeight (double) - Weighting for load balancing.
     * args[8]: cognitiveCoeff (double) - PSO cognitive coefficient (c1).
     * args[9]: socialCoeff (double) - PSO social coefficient (c2).
     * args[10]: inertiaWeight (double) - PSO inertia weight (w).
     *
     * Example: java SLAPSOVmAllocation 10 5 4 20 50 1.2 100.0 50.0 2.0 2.0 0.5
     */
    public static void main(String[] args) {
        try {
            // Default values for parameters (tuned values from previous steps)
            int numVMs = 60;
            int numHosts = 10;
            int landscape = 3;
            int swarmSize = 50; // Optimized
            int maxIterations = 100; // Optimized r
            double scalingFactor = 1.2;
            double slaCostFactor = 100.0;
            double lbWeight = 50.0;
            double cognitiveCoeff = 2.0; // Optimized
            double socialCoeff = 2.0;    // Optimized
            double inertiaWeight = 0.5;  // Optimized

            // Parse command-line arguments if provided
            if (args.length >= 11) { // Now expecting 11 arguments
                numVMs = Integer.parseInt(args[0]);
                numHosts = Integer.parseInt(args[1]);
                landscape = Integer.parseInt(args[2]);
                swarmSize = Integer.parseInt(args[3]);
                maxIterations = Integer.parseInt(args[4]);
                scalingFactor = Double.parseDouble(args[5]);
                slaCostFactor = Double.parseDouble(args[6]);
                lbWeight = Double.parseDouble(args[7]);
                cognitiveCoeff = Double.parseDouble(args[8]);
                socialCoeff = Double.parseDouble(args[9]);
                inertiaWeight = Double.parseDouble(args[10]);
            } else {
                System.out.println("Using default parameters. For custom parameters, provide all 11 arguments.");
            }

            // Define base filenames. VM CSV filename now includes numVMs to make it distinct.
            String baseVmCsvFile = "generated_vm_characteristics_sla-based_VMs" + numVMs + ".csv";
            String baseResultsCsv = "simulation_results_sla-based_VMs" + numVMs + "_H" + numHosts + "_L" + landscape + "_S" + String.format("%.1f", scalingFactor) + ".csv";

            // Get unique file names.
            String vmCsvFile = getUniqueFilename(baseVmCsvFile);
            String resultsCsv = getUniqueFilename(baseResultsCsv);

            // Generate VM characteristics and export to CSV.
            List<VmData> vmList = generateVmDataList(numVMs);
            writeVmDataToCSV(vmList, vmCsvFile); // This will generate the unique VM input file for other algorithms
            System.out.println("Generated VM characteristics have been written to " + vmCsvFile);

            // Run PSO optimization.
            HostCapacity[] hostCaps = createHostCapacities(numHosts, landscape);
            Particle bestParticle = psoOptimization(vmList, hostCaps, numHosts, swarmSize, maxIterations,
                    scalingFactor, slaCostFactor, lbWeight, // Existing params
                    cognitiveCoeff, socialCoeff, inertiaWeight); // New PSO tunable params
            Map<Integer, Integer> vmAllocationMap = new HashMap<>();
            for (int i = 0; i < numVMs; i++) {
                vmAllocationMap.put(vmList.get(i).vmId, bestParticle.allocation[i]);
            }

            // Initialize CloudSim.
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUsers, calendar, traceFlag);
            Datacenter datacenter = createDatacenter("Datacenter_0", hostCaps);
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // Create simulation VMs.
            List<Vm> vmSimList = new ArrayList<>();
            for (VmData vmData : vmList) {
                Vm vm = new Vm(vmData.vmId, brokerId, vmData.mips, 1,
                        vmData.ram, vmData.bw, vmData.size, "Xen", new CloudletSchedulerTimeShared());
                vmSimList.add(vm);
            }
            broker.submitVmList(vmSimList);

            // Create simulation Cloudlets.
            List<Cloudlet> cloudletList = new ArrayList<>();
            for (VmData vmData : vmList) {
                int pesNumber = 1;
                Cloudlet cloudlet = new Cloudlet(vmData.vmId, vmData.cloudletLength, pesNumber,
                        vmData.size, vmData.size, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                cloudlet.setUserId(brokerId);
                cloudletList.add(cloudlet);
            }
            broker.submitCloudletList(cloudletList);

            // Run the simulation.
            CloudSim.startSimulation();
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Calculate Performance Metrics.
            int slaViolations = 0;
            int failedAllocations = 0;
            double totalResponseTime = 0;
            double totalTurnaroundTime = 0;
            double totalQosIndexSum = 0; // Added for QoS Index calculation
            double makespan = 0;
            double minSubmissionTime = Double.MAX_VALUE;
            double maxFinishTime = 0;
            for (Cloudlet cl : finishedCloudlets) {
                if (cl.getCloudletStatus() != Cloudlet.SUCCESS) {
                    failedAllocations++;
                }
                double responseTime = cl.getExecStartTime() - cl.getSubmissionTime();
                double turnaroundTime = cl.getFinishTime() - cl.getSubmissionTime();
                totalResponseTime += responseTime;
                totalTurnaroundTime += turnaroundTime;
                minSubmissionTime = Math.min(minSubmissionTime, cl.getSubmissionTime());
                maxFinishTime = Math.max(maxFinishTime, cl.getFinishTime());
            }
            makespan = maxFinishTime - minSubmissionTime;
            double avgResponseTime = finishedCloudlets.isEmpty() ? 0 : totalResponseTime / finishedCloudlets.size();
            double avgTurnaroundTime = finishedCloudlets.isEmpty() ? 0 : totalTurnaroundTime / finishedCloudlets.size();
            double throughput = finishedCloudlets.isEmpty() ? 0 : finishedCloudlets.size() / makespan;

            // Update SLA violation using a QoS Composite Metric.
            // Define weights: α = 0.6, β = 0.3, γ = 0.1.
            final double alpha = 0.6;
            final double beta = 0.3;
            final double gamma = 0.1;
            for (Cloudlet cl : finishedCloudlets) {
                VmData vm = vmList.get(cl.getCloudletId());
                double actualExecTime = cl.getFinishTime() - cl.getExecStartTime();
                double turnaroundTime = cl.getFinishTime() - cl.getSubmissionTime();
                double responseTime = cl.getExecStartTime() - cl.getSubmissionTime();
                double qosIndex = alpha * (actualExecTime / vm.deadline)
                                  + beta * (turnaroundTime / vm.deadline)
                                  + gamma * (responseTime / vm.deadline);

                totalQosIndexSum += qosIndex; // Accumulate for average
                if (qosIndex > 1.0) { // If QoS Index exceeds 1.0, it indicates an SLA violation.
                    slaViolations++;
                }
            }
            double avgQosIndex = finishedCloudlets.isEmpty() ? 0 : totalQosIndexSum / finishedCloudlets.size();


            double[] hostUtilization = new double[numHosts];
            for (VmData vm : vmList) {
                int hostIndex = vmAllocationMap.get(vm.vmId);
                hostUtilization[hostIndex] += vm.mips;
            }
            double sumCpuUtil = 0;
            for (int i = 0; i < numHosts; i++) {
                double util = hostUtilization[i] / hostCaps[i].mips;
                hostUtilization[i] = util;
                sumCpuUtil += util;
            }
            double avgHostCpuUtil = numHosts > 0 ? sumCpuUtil / numHosts : 0;
            double stdDevCpuUtil = 0;
            for (int i = 0; i < numHosts; i++) {
                stdDevCpuUtil += Math.pow(hostUtilization[i] - avgHostCpuUtil, 2);
            }
            stdDevCpuUtil = numHosts > 0 ? Math.sqrt(stdDevCpuUtil / numHosts) : 0;

            // Print nicely padded output.
            // a) VM Allocation Table.
            System.out.println("\n=== VM Allocation Table (SLA-based PSO) ===");
            System.out.printf("%-5s %-10s %-8s %-8s %-8s %-25s %-20s %-20s%n",
                    "VMID", "STATUS", "MIPS", "RAM", "BW", "PredictedExecTime", "Deadline", "FailureReason");
            for (VmData vm : vmList) {
                int hostIndex = vmAllocationMap.get(vm.vmId);
                double predictedTime = (vm.cloudletLength / hostCaps[hostIndex].mips) * scalingFactor;
                String status = (predictedTime <= vm.deadline) ? "SUCCESS" : "FAIL";
                String failureReason = (predictedTime <= vm.deadline) ? "" : "SLA Violation (Predicted)";
                System.out.printf("%-5d %-10s %-8d %-8d %-8d %-25s %-20s %-20s%n",
                        vm.vmId, status, vm.mips, vm.ram, vm.bw,
                        String.format("%.2f", predictedTime), String.format("%.2f", vm.deadline), failureReason);
            }

            // b) Cloudlet Output with ActualExecutionTime.
            System.out.println("\n=== Cloudlet Output (SLA-based PSO) ===");
            System.out.printf("%-12s %-8s %-10s %-8s %-8s %-8s %-12s %-20s %-20s%n",
                    "CloudletID", "VMID", "STATUS", "VM_MIPS", "VM_RAM", "VM_BW",
                    "DatacenterID", "ActualExecutionTime", "FailureReason");
            for (Cloudlet cl : finishedCloudlets) {
                VmData vm = vmList.get(cl.getCloudletId());
                String status = cl.getCloudletStatus() == Cloudlet.SUCCESS ? "SUCCESS" : "FAIL";
                String failureReason = cl.getCloudletStatus() == Cloudlet.SUCCESS ? "" : "Execution Error";
                double actualExecTime = cl.getFinishTime() - cl.getExecStartTime();
                System.out.printf("%-12d %-8d %-10s %-8d %-8d %-8d %-12d %-20s %-20s%n",
                        cl.getCloudletId(), vm.vmId, status, vm.mips, vm.ram, vm.bw, cl.getResourceId(),
                        String.format("%.2f", actualExecTime), failureReason);
            }

            // c) Host Usage Summary.
            System.out.println("\n=== Host Usage Summary (SLA-based PSO) ===");
            System.out.printf("%-15s %-8s %-20s %-20s%n",
                    "Datacenter", "HostID", "CPU_Utilization(%)", "RAM_Utilization(%)");
            for (int i = 0; i < numHosts; i++) {
                double cpuUtilPercent = hostUtilization[i] * 100;
                double totalRamAllocated = 0;
                for (VmData vm : vmList) {
                    if (vmAllocationMap.get(vm.vmId) == i) {
                        totalRamAllocated += vm.ram;
                    }
                }
                double ramUtilPercent = Math.min(100, (totalRamAllocated / hostCaps[i].ram) * 100);
                System.out.printf("%-15s %-8d %-20s %-20s%n",
                        "Datacenter_0", i, String.format("%.2f", cpuUtilPercent),
                        String.format("%.2f", ramUtilPercent));
            }

            // d) Performance Metrics.
            System.out.println("\n=== Performance Metrics (SLA-based PSO) ===");
            System.out.printf("%-15s %-20s %-20s %-20s %-10s %-10s %-20s %-20s %-15s%n", // Added Avg_QoS_Index
                    "SLA_Violations", "Failed_Allocations", "Avg_ResponseTime", "Avg_TurnaroundTime",
                    "Makespan", "Throughput", "Avg_Host_CPU_Util", "StdDev_Host_CPU_Util", "Avg_QoS_Index"); // Added Avg_QoS_Index
            System.out.printf("%-15d %-20d %-20s %-20s %-10s %-10s %-20s %-20s %-15s%n", // Added Avg_QoS_Index
                    slaViolations, failedAllocations,
                    String.format("%.2f", avgResponseTime), String.format("%.2f", avgTurnaroundTime),
                    String.format("%.2f", makespan), String.format("%.2f", throughput),
                    String.format("%.2f", avgHostCpuUtil * 100), String.format("%.2f", stdDevCpuUtil * 100),
                    String.format("%.4f", avgQosIndex)); // Added Avg_QoS_Index

            // Export results to a new CSV file (using unique filename).
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultsCsv, true))) {
                // VM Allocation Table.
                writer.write("=== VM Allocation Table (SLA-based PSO) ===");
                writer.newLine();
                writer.write("VMID,STATUS,MIPS,RAM,BW,PredictedExecutionTime,Deadline,FailureReason");
                writer.newLine();
                for (VmData vm : vmList) {
                    int hostIndex = vmAllocationMap.get(vm.vmId);
                    double predictedTime = (vm.cloudletLength / hostCaps[hostIndex].mips) * scalingFactor;
                    String status = predictedTime <= vm.deadline ? "SUCCESS" : "FAIL";
                    String failureReason = predictedTime <= vm.deadline ? "" : "SLA Violation (Predicted)";
                    writer.write(vm.vmId + "," + status + "," + vm.mips + "," + vm.ram + "," + vm.bw + ","
                            + String.format("%.2f", predictedTime) + "," + vm.deadline + "," + failureReason);
                    writer.newLine();
                }
                writer.newLine();
                // Cloudlet Output.
                writer.write("=== Cloudlet Output (SLA-based PSO) ===");
                writer.newLine();
                writer.write("CloudletID,VMID,STATUS,VM_MIPS,VM_RAM,VM_BW,DatacenterID,ActualExecutionTime,FailureReason");
                writer.newLine();
                for (Cloudlet cl : finishedCloudlets) {
                    VmData vm = vmList.get(cl.getCloudletId());
                    String status = cl.getCloudletStatus() == Cloudlet.SUCCESS ? "SUCCESS" : "FAIL";
                    String failureReason = cl.getCloudletStatus() == Cloudlet.SUCCESS ? "" : "Execution Error";
                    double actualExecTime = cl.getFinishTime() - cl.getExecStartTime();
                    writer.write(cl.getCloudletId() + "," + vm.vmId + "," + status + "," + vm.mips + ","
                            + vm.ram + "," + vm.bw + "," + cl.getResourceId() + ","
                            + String.format("%.2f", actualExecTime) + "," + failureReason);
                    writer.newLine();
                }
                writer.newLine();
                // Host Usage Summary.
                writer.write("=== Host Usage Summary (SLA-based PSO) ===");
                writer.newLine();
                writer.write("Datacenter,HostID,CPU_Utilization(%),RAM_Utilization(%)");
                writer.newLine();
                for (int i = 0; i < numHosts; i++) {
                    double cpuUtilPercent = hostUtilization[i] * 100;
                    double totalRamAllocated = 0;
                    for (VmData vm : vmList) {
                        if (vmAllocationMap.get(vm.vmId) == i) {
                            totalRamAllocated += vm.ram;
                        }
                    }
                    double ramUtilPercent = Math.min(100, (totalRamAllocated / hostCaps[i].ram) * 100);
                    writer.write("Datacenter_0," + i + "," + String.format("%.2f", cpuUtilPercent) + ","
                            + String.format("%.2f", ramUtilPercent));
                    writer.newLine();
                }
                writer.newLine();
                // Performance Metrics.
                writer.write("=== Performance Metrics (SLA-based PSO) ===");
                writer.newLine();
                writer.write("SLA_Violations,Failed_Allocations,Avg_ResponseTime,Avg_TurnaroundTime,Makespan,Throughput,Avg_Host_CPU_Util,StdDev_Host_CPU_Util,Avg_QoS_Index"); // Added Avg_QoS_Index
                writer.newLine();
                writer.write(slaViolations + "," + failedAllocations + ","
                        + String.format("%.2f", avgResponseTime) + "," + String.format("%.2f", avgTurnaroundTime) + ","
                        + String.format("%.2f", makespan) + "," + String.format("%.2f", throughput) + ","
                        + String.format("%.2f", avgHostCpuUtil * 100) + "," + String.format("%.2f", stdDevCpuUtil * 100) + ","
                        + String.format("%.4f", avgQosIndex)); // Added Avg_QoS_Index
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("\nSimulation results exported to " + resultsCsv);

            // Log comparison results to the shared CSV
            writeComparisonResultsToCSV(
                "SLA_PSO", numVMs, numHosts, landscape, scalingFactor,
                slaViolations, failedAllocations, avgResponseTime, avgTurnaroundTime,
                makespan, throughput, avgHostCpuUtil, stdDevCpuUtil, avgQosIndex
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
