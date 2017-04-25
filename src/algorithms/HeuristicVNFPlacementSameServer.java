package algorithms;


import main.VNFPSimulation;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.util.*;

// 该算法会导致启动的VNF类型不均一
public class HeuristicVNFPlacementSameServer implements VNFPlacement{

    private Topology topo;
    private int k;

    private double[][] vnfCountMatrix;

    private double para1;
    private double para2;
    private double para3;

    public HeuristicVNFPlacementSameServer(Topology topo, double para1, double para2, double para3) {
        this.topo = topo;
        this.k = ((FatTreeTopo)topo).getK();
        this.para1 = para1;
        this.para2 = para2;
        this.para3 = para3;
        this.vnfCountMatrix = new double[VNFPSimulation.vnfSum + 1][VNFPSimulation.vnfSum + 1];
        for(int i = 1; i < VNFPSimulation.vnfSum + 1; i++) {
            this.vnfCountMatrix[0][i] = i % 10;
            this.vnfCountMatrix[i][0] = i % 10;
        }
    }

    @Override
    public void countVnf(NetworkService ns) {
        LinkedList<VirtualNetworkFunction> sfcList = ns.sfcList;
        Iterator sfcIterator = sfcList.iterator();
        int lastVnf = ((VirtualNetworkFunction) sfcIterator.next()).vnfType;
        while(sfcIterator.hasNext()) {
            int thisVnf = ((VirtualNetworkFunction) sfcIterator.next()).vnfType;
            vnfCountMatrix[lastVnf][thisVnf]++;
            lastVnf = thisVnf;
        }
    }

    @Override
    public boolean isMostRelated(int lastVNF, int thisVNF) {
        return true;
    }

    @Override
    public boolean inSameGroup(int lastVNF, int thisVNF) {
        return false;
    }

    @Override
    public void showVnfCountMatrix() {
        System.out.println("the vnf count Matrix is :");
        for(int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            for(int j = 0; j < VNFPSimulation.vnfSum + 1; j++) {
                if(vnfCountMatrix[i][j] == 0) {
                    System.out.print("   " + "   ");
                }else {
                    System.out.print(vnfCountMatrix[i][j] + "   ");
                }
            }
            System.out.println();
        }
    }

    @Override
    public int[][] getVnfCountMatrix() {
        int[][] tmp = new int[VNFPSimulation.vnfSum + 1][VNFPSimulation.vnfSum + 1];
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            for (int j = 0; j < VNFPSimulation.vnfSum + 1; j++) {
                tmp[i][j] = (int) vnfCountMatrix[i][j];
            }
        }
        return tmp;
    }

    @Override
    public VirtualMachine placeVNFInTopo(VirtualNetworkFunction v) {
        VirtualMachine availableVM = getAvailableVMInTopo(v);
        if(availableVM != null) {
//            System.out.print("existed -- ");
            return availableVM;
        }
        VirtualMachine vm = generateVnf(v.vnfType);
        PhysicalServer server = whichServerInTopo(vm);
        if(server == null) {
            return null;
        } else {
            topo.launchVM(server, vm);
            return vm;
        }
    }

    @Override
    public VirtualMachine placeVNFInServer(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr) {
        PhysicalServer thisServer = topo.getServerByAddress(serverAddr);
        Set<PhysicalServer> serverSet = new HashSet<>();
        serverSet.add(thisServer);
        VirtualMachine vm = generateVnf(virtualNetworkFunction.vnfType);

        // 判断这个server上有没有可用的VNF
        VirtualMachine availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
        // 这个server上有现成的VNF
        if(availableVM != null) {
            // System.out.print("existed -- ");
            return availableVM;
        }
        // 这个server上没有现成的VNF，尝试在该server上新建VNF
        if(thisServer.isResEnough(vm.cpu, vm.ram)) {
            this.topo.launchVM(thisServer, vm);
            // System.out.print("new -- ");
            return vm;
        }

        // 如果在server上没有足够的资源，在同一个rack中去部署
        return placeVNFInRack(virtualNetworkFunction, serverAddr);
    }

    @Override
    public VirtualMachine placeVNFInRack(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr) {
        PhysicalServer thisServer = topo.getServerByAddress(serverAddr);
        Set<PhysicalServer> serverSet = new HashSet<>();
//        serverSet.add(thisServer);
        VirtualMachine vm = generateVnf(virtualNetworkFunction.vnfType);

        short podId = thisServer.getAddress().getPod();
        short edgeId = thisServer.getAddress().getEdge();
        // 获取与server相距为1的server的集合，同一个edgeSwitch上面的server
        PhysicalServer[] servers = topo.getServerByEdge(podId, edgeId);
        for(int i = 1; i <= k / 2; i++) {
            serverSet.add(servers[i]);
        }

        // 判断当前的与sever在同一个rack上的server集合中有无现成的VNF
        VirtualMachine availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
        // 集合中有现成的VNF
        if(availableVM != null) {
//            System.out.print("existed -- ");
            return availableVM;
        }

        // 集合中没有现成的VNF，需要新建
        PhysicalServer server = whichServerInSet(vm, serverSet);
        if(server != null) {
            this.topo.launchVM(server, vm);
//            System.out.print("new -- ");
            return vm;
        }

        // 如果rack中没有足够的资源去新建VNF，placeVNFInTopo
        return placeVNFInTopo(virtualNetworkFunction);
    }

    private VirtualMachine getAvailableVMInTopo(VirtualNetworkFunction v) {
        for(short i = 1; i <= k; i++) {
            PhysicalServer[][] physicalServers = topo.getServerByPod(i);
            for(int j = 1; j <= k / 2; j++) {
                for(int h = 1; h <= k / 2; h++) {
                    Set<VirtualMachine> vmSet = physicalServers[j][h].getVmSet();
                    for(VirtualMachine vm : vmSet) {
                        if(vm.vnfType == v.vnfType && vm.isResEnough(v.mipsNeeded))
                            return vm;
                    }
                }
            }
        }
        return null;
    }

    private VirtualMachine getAvailableVMInServerSet(Set<PhysicalServer> serverSet, VirtualNetworkFunction v) {
        for(PhysicalServer server : serverSet) {
            Set<VirtualMachine> vmSet = server.getVmSet();
            for(VirtualMachine vm : vmSet) {
                if(vm.vnfType == v.vnfType && vm.isResEnough(v.mipsNeeded))
                    return vm;
            }
        }
        return null;
    }

    private PhysicalServer whichServerInSet(VirtualMachine virtualMachine, Set<PhysicalServer> serverSet) {
        Set<PhysicalServer> filteredServers = filterServersInSet(virtualMachine.cpu, virtualMachine.ram, serverSet);
        if(filteredServers.isEmpty()) {
            return null;
        } else {
            return weightServerInSet(filteredServers);
        }
    }

    private Set<PhysicalServer> filterServersInSet(int cpu, int ramNeeded, Set<PhysicalServer> serverSet) {
        Set<PhysicalServer> servers = new HashSet<>();
        for(PhysicalServer s : serverSet) {
            if(s.isResEnough(cpu, ramNeeded))
                servers.add(s);
        }
        return servers;
    }

    private PhysicalServer whichServerInTopo(VirtualMachine virtualMachine) {
        Set<PhysicalServer> filteredServers = filterServersInTopo(virtualMachine.cpu, virtualMachine.ram);
        if(filteredServers.isEmpty()) {
            return null;
        } else {
            return this.weightServerInSet(filteredServers);
        }
    }

    private Set<PhysicalServer> filterServersInTopo(int cpuNeeded, int ramNeeded) { // checked!
        Set<PhysicalServer> servers = new HashSet<>();
        int k = ((FatTreeTopo)this.topo).getK();
        for(int i = 1; i <= k; i++) {
            for(int j = 1; j <= k/2; j++) {
                for(int h = 1; h <= k/2; h++) {
                    PhysicalServer server = topo.getServerById(i, j, h);
                    if(server.isResEnough(cpuNeeded, ramNeeded))
                        servers.add(server);
                }
            }
        }
        return servers;
    }



    //将edgeSwitch上过滤过得server排序，直接返回得分最高的server
    private PhysicalServer weightServerInSet(Set<PhysicalServer> serverSet) {
        // 转化成arrayList
        ArrayList<PhysicalServer> servers = new ArrayList<>();
        serverSet.forEach(servers::add);

        double[] multiplier = new double[]{para1, para2, para3};
        int size = servers.size();
        double[][] decisionM = new double[size][3];
        // 计算决策矩阵
        for(int i = 0; i < size; i++) {
            PhysicalServer s = servers.get(i);
            decisionM[i][0] = s.getCpuCoreRemain();
            decisionM[i][1] = s.getRamRemain();
            decisionM[i][2] = s.getTrafficIO();
        }
        // 数据归一化
        decisionM = normalizeM(decisionM, size);
        // 加权
        for(int i = 0; i < size; i++) {
            for(int j = 0; j < 3; j++) {
                decisionM[i][j] *= multiplier[j];
            }
        }
        // 找出得分最高的server
        int maxIndex = 0;
        double max = decisionM[0][0] + decisionM[0][1] + decisionM[0][2];
        for(int i = 0; i < size; i++) {
            if((decisionM[i][0] + decisionM[i][1] + decisionM[i][2]) > max) {
                maxIndex = i;
            }
        }
        return servers.get(maxIndex);
    }

    private double[][] normalizeM(double[][] m, int size){
        for(int i = 0; i < 3; i++) {
            double max = m[0][i];
            double min = m[0][i];
            for(int j = 0; j < size; j++) {
                if(m[j][i] > max) {
                    max = m[j][i];
                }
                if(m[j][i] < min) {
                    min = m[j][i];
                }
            }
            if((max - min) != 0){
                for(int j = 0; j < size; j++) {
                    m[j][i] = (m[j][i] - min)/(max - min);
                }
                // 当最大值和最小值相等时候，该项分数就没有意义了，全部置0
            } else {
                for(int j = 0; j < size; j++) {
                    m[j][i] = 0;
                }
            }
        }
        return m;
    }

    private VirtualMachine generateVnf(int vnfType) {
        return new VirtualMachine(1, 1024, vnfType);
    }

    private int randomUniformInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }

}

