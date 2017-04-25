package model;

/**
 * @author Janon Wang
 */
public class VirtualMachine {

    public int cpu;
    public int ram;
    public int cpuCapacity; // MIPS
    public int cpuRemain;
    public int ramRemain;
    public int vnfType;
    public int vnfNumber;
    public PhysicalServer position;

    public VirtualMachine(int cpuNeeded, int ramNeeded, int vnfType) {
        this.cpu = cpuNeeded;
        // 假设一个cpu能承担10000MIPS的命令
        this.cpuCapacity = cpuNeeded*10000;
        this.cpuRemain = cpuCapacity;
        this.ram = ramNeeded;
        this.ramRemain = ramNeeded;
        this.vnfType = vnfType;
        this.vnfNumber = 0;
    }

//    public boolean isResEnough(int cpuConsume, int ramConsume) {
//        return cpuConsume < cpuRemain && ramConsume < ramRemain;
//    }

    public boolean isResEnough(int cpuConsume) {
        return cpuConsume < cpuRemain;
    }

//    public boolean launchVnf(int cpuConsume, int ramConsume) {
//        if(cpuConsume < cpuRemain && ramConsume < ramRemain) {
//            cpuRemain -= cpuConsume;
//            ramRemain -= ramConsume;
//            this.vnfNumber ++;
//            return true;
//        } else {
//            return false;
//        }
//    }

    public boolean launchVnf(VirtualNetworkFunction v) {
        if(v.mipsNeeded < cpuRemain) {
            cpuRemain -= v.mipsNeeded;
            this.position.addTrafficIO(v.trafficRate);
            this.vnfNumber ++;
            v.position = this.position.getAddress();
            return true;
        } else {
            return false;
        }
    }
}
