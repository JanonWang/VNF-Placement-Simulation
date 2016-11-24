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
    private int VMCount;
    private Set<VirtualMachine> virtualMachineSet; // the virtual machine launched at server will be record

    private final int cpuCoreCapacity = 50; // every server has 50 cpu cores
    private final int ramCapacity = 131072; // every server has 131072 MB ram

    private int cpuCoreUsed;
    private int ramUsed;

    private int cpuCoreRemain;
    private int ramRemain;


    public PhysicalServer(Address address) {
        this.addr = address;
        this.vnfCount = new int[VNFPSimulation.vnfSum + 1];
        // 初始化vnf的统计，全部置0
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            vnfCount[i] = 0;
        }
        this.VMCount = 0;
        virtualMachineSet = new HashSet<>();
        this.cpuCoreUsed = 0;
        this.ramUsed = 0;
        this.cpuCoreRemain = this.cpuCoreCapacity;
        this.ramRemain = this.ramCapacity;
    }

    public int getCpuCoreRemain() {
        return this.cpuCoreRemain;
    }

    public int getRamRemain() {
        return this.ramRemain;
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

    // FIXME --- 还需要对pod和edgeSwitch中的vnf数量也增加
    public boolean launchVM(VirtualMachine vm) {
        if(vm.cpu <= cpuCoreRemain && vm.ram <= ramRemain) {
            consumeCpu(vm.cpu);
            consumeRam(vm.ram);
            VMCount ++;
            virtualMachineSet.add(vm);
            vnfCount[vm.vnfType]++;
            return true;
        } else {
            return false;
        }
    }

    private void consumeCpu(int cpuConsumed) {
            cpuCoreUsed += cpuConsumed;
            cpuCoreRemain -= cpuConsumed;
    }

    private void consumeRam(int ramConsume) {
            ramUsed += ramConsume;
            ramRemain -= ramConsume;
    }

    public void addVnf(int vnfType) {
        vnfCount[vnfType] ++;
    }

    public int[] getVnfCounts() {
        return vnfCount;
    }

    public int getVnfCount(int vnfType) {
        return vnfCount[vnfType];
    }

    public void addVM(VirtualMachine v) {
        virtualMachineSet.add(v);
    }

    public int getVMCount() {
        return VMCount;
    }

    public Set<VirtualMachine> getVMSet() {
        return virtualMachineSet;
    }

    public String getName() {
        return "Server" + addr.getServer();
    }
}
