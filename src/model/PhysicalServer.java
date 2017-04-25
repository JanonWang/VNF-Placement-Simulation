package model;

import main.VNFPSimulation;
import sun.misc.VM;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Janon Wang
 */
public class PhysicalServer extends Node {

    private Link edge;
    private int[] vnfCount;
    private Set<VirtualMachine> virtualMachineSet; // the virtual machine launched at server will be record

    private final int cpuCoreCapacity = 5; // FIXME -- 把每个server的cpu改成了20
    private final int ramCapacity = 6*1024; // FIXME -- 每个server的内存改成了32GB

    private int cpuCoreRemain;
    private int ramRemain;

    private int trafficIO;


    public PhysicalServer(Address address) {
        this.addr = address;
        this.vnfCount = new int[VNFPSimulation.vnfSum + 1];
        // 初始化vnf的统计，全部置0
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            vnfCount[i] = 0;
        }
        this.trafficIO = 0;
        this.virtualMachineSet = new HashSet<>();
        this.cpuCoreRemain = this.cpuCoreCapacity;
        this.ramRemain = this.ramCapacity;
    }

    public void setEdgeLink(Link l) {
        this.edge = l;
    }

    public Link getEdgeLink() {
        return this.edge;
    }

    public boolean isResEnough(int cpu, int ram) {
        return isCpuEnough(cpu) && isRamEnough(ram);
    }

    public boolean isResEnough(int cpu) {
        return isCpuEnough(cpu);
    }

    private boolean isCpuEnough(int cpu) {
        return cpu <= cpuCoreRemain;
    }

    private boolean isRamEnough(int ram) {
        return ram <= ramRemain;
    }

    public boolean launchVM(VirtualMachine vm) {
        if(vm.cpu <= cpuCoreRemain && vm.ram <= ramRemain) {
            consumeCpu(vm.cpu);
            consumeRam(vm.ram);
            virtualMachineSet.add(vm);
            vnfCount[vm.vnfType]++;
            return true;
        } else {
            return false;
        }
    }
    private void consumeCpu(int cpuConsumed) {
        cpuCoreRemain -= cpuConsumed;
    }
    private void consumeRam(int ramConsume) {
        ramRemain -= ramConsume;
    }

    public void addTrafficIO(int trafficRate) {
        this.trafficIO += trafficRate;
    }

    public int getCpuCoreRemain() {
        return this.cpuCoreRemain;
    }

    public int getRamRemain() {
        return this.ramRemain;
    }

    public int getTrafficIO() {
        return this.trafficIO;
    }

    public int[] getVnfCounts() {
        return vnfCount;
    }

    public int getVnfCount(int vnfType) {
        return vnfCount[vnfType];
    }

    public String getName() {
        return "Server" + addr.getServer();
    }

    public Set<VirtualMachine> getVmSet() {
        return this.virtualMachineSet;
    }
}
