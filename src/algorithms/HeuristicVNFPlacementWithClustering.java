package algorithms;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import main.VNFPSimulation;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.util.*;

/**
 * @author Janon Wang
 */
public class HeuristicVNFPlacementWithClustering implements VNFPlacement{

    private Topology topo;
    private int k;

    private double[][] vnfCountMatrix;
//    private LinkedList<Integer> vnfQueue;
    // FIXME -- K如何来选择
    private final int cutNumberK = 6; // 暂定为5，分的太细了也不行，造成多个vnf一个一组的情况

    private double para1;
    private double para2;
    private double para3;

    public HeuristicVNFPlacementWithClustering(Topology topo, double para1, double para2, double para3) {
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
//        this.vnfQueue = new LinkedList<>();
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
//        switch (lastVNF) {
//            case 5:
//                return thisVNF == 1;
//            case 10:
//                return thisVNF == 6;
//            case 15:
//                return thisVNF == 11;
//            case 20:
//                return thisVNF == 16;
//            case 25:
//                return thisVNF == 21;
//            case 30:
//                return thisVNF == 26;
//            default:
//                return thisVNF == lastVNF + 1;
//        }
        double[] thisVNFCount = new double[VNFPSimulation.vnfSum + 1];
        for(int i = 1; i <= VNFPSimulation.vnfSum; i++) {
            thisVNFCount[i] = vnfCountMatrix[lastVNF][i];
        }
        double max = 0;
        int maxIndex = 0;
        for(int i = 1; i <= VNFPSimulation.vnfSum; i++) {
            if(thisVNFCount[i] >= max) {
                max = thisVNFCount[i];
                maxIndex = i;
            }
        }
        return maxIndex == thisVNF;
    }

    @Override
    public boolean inSameGroup(int lastVNF, int thisVNF) {
        Set<Integer> vnfGroup = getVnfGroup(getKcut(this.cutNumberK), lastVNF);
        if(vnfGroup == null || vnfGroup.isEmpty()) {
            return false;
        }
        for(Integer i : vnfGroup) {
            if(i == thisVNF) {
                return true;
            }
        }
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
//            System.out.print("new -- ");
//            // 将已经启动过的VNF在队列中做记录
//            addQueue(v.vnfType);
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

//        short podId = thisServer.getAddress().getPod();
//        short edgeId = thisServer.getAddress().getEdge();
//        // 获取与server相距为1的server的集合，同一个edgeSwitch上面的server
//        PhysicalServer[] servers = topo.getServerByEdge(podId, edgeId);
//        for(int i = 1; i <= k / 2; i++) {
//            serverSet.add(servers[i]);
//        }
//
//        // 判断当前的与sever相距为1的server的集合中有无现成的VNF
//        availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
//        // 当前的与sever相距为1的server的集合中有现成的VNF
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//
//        // 当前的与sever相距为1的server的集合中没有现成的VNF，需要新建
//        PhysicalServer server = whichServerInSet(vm, serverSet);
//        if(server != null) {
//            this.topo.launchVM(server, vm);
//            System.out.print("new -- ");
//            return vm;
//        }



//        // 同一个rack中的server没有满足条件的，则在同一个pod中寻找
//        serverSet.clear();
//        PhysicalServer[][] servers1 = topo.getServerByPod(podId);
//        // 将同一个pod中的server放在同一个集合中
//        for(int i = 1; i <= k / 2; i++) {
//            for(int j = 1; j <= k / 2; j++) {
//                serverSet.add(servers1[i][j]);
//            }
//        }
//
//        // 先在同一个pod中寻找有没有可用的VNF，如果没有再新建
//        availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//
//        // pod中没有可用的VNF，需要新建
//        server = whichServerInSet(vm, serverSet);
//        if(server != null) {
//            // 在pod中找到了可用的server
//            topo.launchVM(server, vm);
//            System.out.print("new -- ");
//            return vm;
//        }
//
//        // 同一个pod中的server没有满足条件的，则在整个topo中寻找
//        availableVM = getAvailableVMInTopo(virtualNetworkFunction);
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//
//        server = whichServerInTopo(vm);
//        if(server != null) {
//            // 在整个topo中找到了可用的server
//            topo.launchVM(server, vm);
//            System.out.print("new -- ");
//            return vm;
//        }
//
//        // 整个topo中都没有找到可用的server，则返回null；
//        return null;
    }

    @Override
    public VirtualMachine placeVNFInRack(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr) {
        PhysicalServer thisServer = topo.getServerByAddress(serverAddr);
        Set<PhysicalServer> serverSet = new HashSet<>();
//        serverSet.add(thisServer);
        VirtualMachine vm = generateVnf(virtualNetworkFunction.vnfType);


//        // 判断这个server上有没有可用的VNF
//        VirtualMachine availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
//        // 这个server上有现成的VNF
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//        // 这个server上没有现成的VNF，尝试在该server上新建VNF
//        if(thisServer.isResEnough(vm.cpu, vm.ram)) {
//            this.topo.launchVM(thisServer, vm);
//            System.out.print("new -- ");
//            return vm;
//        }

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



//        // 同一个rack中的server没有满足条件的，则在同一个pod中寻找
//        serverSet.clear();
//        PhysicalServer[][] servers1 = topo.getServerByPod(podId);
//        // 将同一个pod中的server放在同一个集合中
//        for(int i = 1; i <= k / 2; i++) {
//            for(int j = 1; j <= k / 2; j++) {
//                serverSet.add(servers1[i][j]);
//            }
//        }
//
//        // 先在同一个pod中寻找有没有可用的VNF，如果没有再新建
//        availableVM = getAvailableVMInServerSet(serverSet, virtualNetworkFunction);
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//
//        // pod中没有可用的VNF，需要新建
//        server = whichServerInSet(vm, serverSet);
//        if(server != null) {
//            // 在pod中找到了可用的server
//            topo.launchVM(server, vm);
//            System.out.print("new -- ");
//            return vm;
//        }
//
//        // 同一个pod中的server没有满足条件的，则在整个topo中寻找
//        availableVM = getAvailableVMInTopo(virtualNetworkFunction);
//        if(availableVM != null) {
//            System.out.print("existed -- ");
//            return availableVM;
//        }
//
//        server = whichServerInTopo(vm);
//        if(server != null) {
//            // 在整个topo中找到了可用的server
//            topo.launchVM(server, vm);
//            System.out.print("new -- ");
//            return vm;
//        }
//
//        // 整个topo中都没有找到可用的server，则返回null；
//        return null;
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
        int thisVnfType = virtualMachine.vnfType;
        int cpuNeeded =  virtualMachine.cpu;
        int ramNeeded = virtualMachine.ram;
        Set<Set<Integer>> vnfGroups = getKcut(cutNumberK);
        // vnfGroup中包含了和该vnf分在一组的其他vnf的信息
        Set<Integer> vnfGroup = getVnfGroup(vnfGroups, thisVnfType);
        // 该VNF不属于任何组，则将所有的server按资源评分
        if(vnfGroup == null || vnfGroup.isEmpty()) {
            Set<PhysicalServer> serverInTopo = new HashSet<>();
            for(short i = 1; i <= k; i++) {
                PhysicalServer[][] serverInPod = topo.getServerByPod(i);
                for(int j = 1; j <= k/2; j++) {
                    for(int h = 1; h <= k/2 ; h++) {
                        if(serverInPod[j][h].isResEnough(cpuNeeded, ramNeeded))
                            serverInTopo.add(serverInPod[j][h]);
                    }
                }
            }
            Set<PhysicalServer> filteredServer = filterServersInSet(cpuNeeded, ramNeeded,serverInTopo);
            if(!filteredServer.isEmpty())
                return weightServerInSet(filteredServer);
            return null;
        }
        // 如果该VNF属于一个组，则将所有的rack按相关性排序
        Address[] rackRankResult = rankRack(vnfGroup);
        for(Address edgeSwitchAddr : rackRankResult) {
            EdgeSwitch edgeSwitch = (EdgeSwitch) topo.getSwitchByAddress(edgeSwitchAddr);
            Set<PhysicalServer> serverInEdge = new HashSet<>();
            Vector<Link> downLinks = edgeSwitch.getDownLinks();
            for(int i = 1; i < downLinks.size(); i++) {
                serverInEdge.add((PhysicalServer) downLinks.get(i).getDownNode());
            }
            Set<PhysicalServer> filteredServer = filterServersInSet(cpuNeeded, ramNeeded,serverInEdge);
            if(!filteredServer.isEmpty())
                return weightServerInSet(filteredServer);
        }
        // 遍历了全部的rack，都没有找到可行的server，则返回空
        return null;
//        int[] podIdRankResult = rankPod(vnfGroup);
//        for(int podId : podIdRankResult) {
//            int[] edgeSwitchIds = rankRack(vnfGroup, podId);
//            for(int edgeSwitchId : edgeSwitchIds) {
//                EdgeSwitch edgeSwitch = (EdgeSwitch) topo.getSwitchByAddress(new Address(podId, 0, edgeSwitchId, 0));
//                Set<PhysicalServer> serverInEdge = new HashSet<>();
//                Vector<Link> downLinks = edgeSwitch.getDownLinks();
//                for(int i = 1; i < downLinks.size(); i++) {
//                    serverInEdge.add((PhysicalServer) downLinks.get(i).getDownNode());
//                }
//                Set<PhysicalServer> filteredServer = filterServersInSet(cpuNeeded, ramNeeded,serverInEdge);
//                if(!filteredServer.isEmpty())
//                    return weightServerInSet(filteredServer);
//            }
//        }

    }

//    // 当set为空的时候，说明该vnf是单独成一组的，与其他的vnf没有关系，则随机返回pod的rank顺序
//    private int[] rankPod(Set<Integer> vnfGroup) {
//        int k = ((FatTreeTopo)topo).getK();
//        if(vnfGroup.isEmpty()) {
//            int random = randomUniformInt(1, k);
//            int[] rankResult = new int[k];
//            for(int j = 0; j < k; j++) {
//                rankResult[j] = random % k + 1;
//                random++;
//            }
//            return rankResult;
//        } else {
//            Map<Integer, Integer> podVnfCountsMap = new HashMap<>();         // <podId, relatedVnfSum>
//            for(int i = 1; i <= k; i++) {
//                int[] vnfCounts = topo.getPodVnfCounts(i);
//                int relatedVnfSum = 0;
//                for(Integer vnf : vnfGroup) {
//                    relatedVnfSum += vnfCounts[vnf];
//                }
//                podVnfCountsMap.put(i, relatedVnfSum);
//            }
//            return sortMapByValue(podVnfCountsMap);
//        }
//    }

    // 将所有的rack根据其中VNF数量进行排序，有vnfGroup组中的VNF则加一分，没有则减一分
    private Address[] rankRack(Set<Integer> vnfGroup) {
        Map<Address, Integer> edgeVnfCountsMap = new HashMap<>();
        for(int i = 1; i <= k; i++) {
            for(int j =1; j <= k/2; j++) {
                int[] vnfCounts = topo.getEdgeVnfCounts(i, j);
                int vnfCountSum = 0;
                for(int vnfCount : vnfCounts) {
                    vnfCountSum += vnfCount;
                }
                int relatedVnfSum = 0;
                for(Integer vnf : vnfGroup) {
                    relatedVnfSum += vnfCounts[vnf];
                }
                // (relatedVnfSum - (vnfCountSum - relatedVnfSum))表示同一个组中的VNF加一分，不同组的VNF减一分
                edgeVnfCountsMap.put(new Address(i, 0, j, 0), (relatedVnfSum*2 - vnfCountSum));
            }
        }
        return sortMapByValue(edgeVnfCountsMap);
    }

    // order[0] 中存储的key的值的value是最大的 -- debug done!
    private Address[] sortMapByValue(Map<Address, Integer> map) {
        Address[] order = new Address[map.keySet().size()];
        int i = 0;
        for(Address key : map.keySet()){
            order[i] = key;
            i++;
        }
        // bubble sort
        for(int j = order.length -1; j > 0; j--) {
            for(int k = 0; k < j; k++) {
                Address tmp;
                if(map.get(order[k]) < map.get(order[k + 1])) {
                    tmp = order[k];
                    order[k] = order[k + 1];
                    order[k + 1] = tmp;
                }
            }
        }
        return order;
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

//    // 计算server关于vnf的得分，server上关于该组中的vnf组中vnf的数量总和
//    private double calculateVnfScore(PhysicalServer server, int vnfType) {
//        double[] lastVnfCount = new double[VNFPSimulation.vnfSum + 1];
//        double[] nextVnfCount = new double[VNFPSimulation.vnfSum + 1];
//        for(int i = 1; i <= VNFPSimulation.vnfSum; i++){
//            lastVnfCount[i] = vnfCountMatrix[i][vnfType];
//            nextVnfCount[i] = vnfCountMatrix[vnfType][i];
//        }
//        double max1 = lastVnfCount[0];
//        double max2 = nextVnfCount[0];
//        // maxIndex 代表的是与vnfType 最相关的vnf的类型
//        int maxIndex1 = 0;
//        int maxIndex2 = 0;
//        for(int i = 1; i <= VNFPSimulation.vnfSum; i++) {
//            if(lastVnfCount[i] > max1)
//                maxIndex1 = i;
//            if(nextVnfCount[i] > max2)
//                maxIndex2 = i;
//        }
//        return server.getVnfCount(maxIndex1) + server.getVnfCount(maxIndex2);
//    }

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

    // ------------------修改之前的分组情况
    // 3-->7-->9-->12
    // 2-->6-->8-->4-->10
    // 1-->5-->17-->16
    // 11-->13-->14
    // 15, 18, 19, 20
    // ---------------修改之后的分组情况
    // 3-->7-->9-->12-->15
    // 2-->6-->8-->4-->10-->18
    // 1-->5-->17-->16-->19
    // 11-->13-->14-->20
    private Set<Set<Integer>> getKcut(int k) {
        Set<Set<Integer>> setSet = new HashSet<>();
        for(int i = 0; i < 6; i++) {
            Set<Integer> newSet = new HashSet<>();
            for(int j = 1; j <=10; j++) {
                newSet.add(i*10 + j);
            }
            setSet.add(newSet);
        }
        return setSet;
//            add(new HashSet<Integer>() {{add(1);add(2);add(3);add(4);add(5);add(6);add(7);add(8);add(9);add(10);}});
//            add(new HashSet<Integer>() {{add(11);add(12);add(13);add(14);add(15);add(16);add(17);add(18);add(19);add(20);}});
//            add(new HashSet<Integer>() {{add(21);add(22);add(23);add(24);add(25);add(26);add(27);add(28);add(29);add(30);}});
//            add(new HashSet<Integer>() {{add(16);add(17);add(18);add(19);add(20);}});
//            add(new HashSet<Integer>() {{add(21);add(22);add(23);add(24);add(25);}});
//            add(new HashSet<Integer>() {{add(26);add(27);add(28);add(29);add(30);}});


//        showVnfCountMatrix();
        // 深拷贝vnfCountMatrix矩阵
        // 矩阵转化成对称矩阵，有向图变成无向图
//        double[][] vnfCountMatrix1 = new double[vnfCountMatrix.length - 1][vnfCountMatrix.length - 1];
//        for (int i = 1; i < vnfCountMatrix.length; i++) {
//            for (int j = 1; j < vnfCountMatrix.length; j++) {
//                vnfCountMatrix1[i - 1][j - 1] = vnfCountMatrix[i][j] + vnfCountMatrix[j][i];
//            }
//        }
//        // 对角线元素代表元素的度
//        for(int i = 0; i < vnfCountMatrix1.length; i++) {
//            int count = 0;
//            for(int j = 0; j < vnfCountMatrix1.length; j++) {
//                if(vnfCountMatrix1[i][j] > 0)
//                    count ++;
//            }
//            vnfCountMatrix1[i][i] = count;
//        }
//        int cutNumber = getCutNumber(vnfCountMatrix1);
//        // 当划分的子图数量大于等于要求的子图数量的时候，停止划分子图
//        while(cutNumber < k) {
//            removeMinEdgeOfMatrix(vnfCountMatrix1);
//            cutNumber = getCutNumber(vnfCountMatrix1);
//        }
//        return getSubGraph(vnfCountMatrix1);
    }

    // 根据拉普拉斯矩阵计算子图的数量，拉普拉斯矩阵的特征值为0的个数，即为图的子图数量
    private int getCutNumber(double[][] m) {
        // 深拷贝，转换成拉普拉斯矩阵，对角线元素不用改了
        double[][] mTmp = new double[m.length][];
        for(int i = 0; i < m.length; i++) {
            mTmp[i] = m[i].clone();
        }
        for(int i = 0; i < mTmp.length; i++) {
            for(int j = 0; j < mTmp.length; j++) {
                if(i == j)
                    continue;
                if(mTmp[i][j] > 0)
                    mTmp[i][j] = -1;
            }
        }
        int cutNumber = 0;
        Matrix mTmp1 = new Matrix(mTmp);
        EigenvalueDecomposition eigenvalueDecomposition = new EigenvalueDecomposition(mTmp1);
        double[] eigenValues = eigenvalueDecomposition.getRealEigenvalues();
        for(double eigenValue : eigenValues) {
            // 算出来的结果有一点点的偏差，不是正好是整数
            if(Math.abs(eigenValue - 0) < 0.001)
                cutNumber++;
        }
        return cutNumber;
    }

    // 去掉图中权值最小的边
    private void removeMinEdgeOfMatrix(double[][] matrix) {
        // 初始化一个较大的值
        double mini = 999999;
        int miniRow = 0;
        int miniColumn = 1;
        for(int i = 0; i < matrix.length; i++) {
            for(int j = i + 1; j <matrix.length; j++) {
                if(matrix[i][j] < mini && matrix[i][j] > 0) {
                    mini = matrix[i][j];
                    miniRow = i;
                    miniColumn = j;
                }
            }
        }
        // 将最小的边置0
        // System.out.println("miniRow: " + miniRow + "miniColumn: " + miniColumn);
        matrix[miniRow][miniColumn] = 0;
        matrix[miniColumn][miniRow] = 0;
        matrix[miniColumn][miniColumn]--;
        matrix[miniRow][miniRow]--;
    }

    // 根据邻接矩阵来划分子图，子图中的元素放在同一个set中 --- debug finished！
    private Set<Set<Integer>> getSubGraph(double[][] matrix) {
// 所有的边
        Set<Pair> pairs = new HashSet<>();
        for(int i = 0; i < matrix.length; i++) {
            for(int j = i+1; j < matrix.length; j++) {
                if(matrix[i][j] > 0)
                    pairs.add(new Pair(i + 1, j + 1));
            }
        }
        // 所有的点集合
        Map<Integer, Set<Integer>> vnfGroups = new HashMap<>();
        // 记录vnf属于哪个集合
        int[] record = new int[matrix.length + 1];
        // 集合的序号，从1开始编号
        int setNumber = 1;
        for(Pair pair : pairs) {
            int vnf1 = pair.vnf1;
            int vnf2 = pair.vnf2;
            if(record[vnf1] == 0 && record[vnf2] == 0) {
                Set<Integer> vnfGroup = new HashSet<>();
                vnfGroups.put(setNumber, vnfGroup);
                vnfGroup.add(vnf1);
                vnfGroup.add(vnf2);
                record[vnf1] = setNumber;
                record[vnf2] = setNumber;
            } else if(record[vnf1] == 0 && record[vnf2] != 0) {
                vnfGroups.get(record[vnf2]).add(vnf1);
                record[vnf1] = record[vnf2];
            } else if(record[vnf1] != 0 && record[vnf2] == 0) {
                vnfGroups.get(record[vnf1]).add(vnf2);
                record[vnf2] = record[vnf1];
            } else {
                // 如果两个点都标记过了且他们所在的集合不是同一个，则需要将这两个集合合并
                if(record[vnf1] != record[vnf2]) {
                    Set<Integer> vnf1Set = vnfGroups.get(record[vnf1]);
                    int vnf2Record = record[vnf2];
                    for(Integer i : vnfGroups.get(record[vnf2])) {
                        record[i] = record[vnf1];
                        vnf1Set.add(i);
                    }
                    vnfGroups.remove(vnf2Record);
                    record[vnf2] = record[vnf1];
                }
            }
            setNumber++;
        }
        Set<Set<Integer>> setset = new HashSet<>();
        vnfGroups.keySet().forEach( key -> setset.add(vnfGroups.get(key)));
        // 对于unMarked集合中遗留的单个的点的处理,一个点一个集合
        for(int i = 1; i < record.length; i++) {
            if(record[i] == 0) {
                Set<Integer> singleSet = new HashSet<>();
                singleSet.add(i);
                setset.add(singleSet);
            }
        }
        return setset;
    }

    // 辅助类
    private class Pair{
        int vnf1;
        int vnf2;
        Pair(int vnf1, int vnf2){
            this.vnf1 = vnf1;
            this.vnf2 = vnf2;
        }
    }

    // 根据子图信息，找到该元素所属的子图，并返回该子图中除了该元素之外的元素
    private Set<Integer> getVnfGroup(Set<Set<Integer>> vnfGroups, int vnfType) {
        boolean isFind = false;
        for(Set<Integer> vnfGroup : vnfGroups) {
            for(Integer vnf : vnfGroup) {
                if(vnf == vnfType) {
                    isFind = true;
                    break;
                }
            }
            if(isFind) {
                vnfGroup.remove(vnfType);
                return vnfGroup;
            }
        }
        System.err.println("can not find the vnfType --- HeuristicVNFPlacementWithClustering.getVnfGroup");
        return null;
    }

    private VirtualMachine generateVnf(int vnfType) {
//        int count = getCountInQueue(vnfType);
        return new VirtualMachine(1, 1024, vnfType);
//        switch(count) {
//            case 0:
//                // VirtualMachine(int cpuNeeded, int ramNeeded, int vnfType)
//                return new VirtualMachine(1, 1024, vnfType);
//            case 1:
//                return new VirtualMachine(2, 2048, vnfType);
//            case 2:
//                return new VirtualMachine(3, 4096, vnfType);
//            case 3:
//                return new VirtualMachine(4, 8192, vnfType);
//            default:
//                return new VirtualMachine(4, 8192, vnfType);
//        }
    }

//    private void addQueue(int vnfType) {
//        int queueLimit = 20;
//        if(vnfQueue.size() >= queueLimit) {
//            vnfQueue.removeLast();
//            vnfQueue.addFirst(vnfType);
//        } else {
//            vnfQueue.addFirst(vnfType);
//        }
//    }

//    private int getCountInQueue(int vnfType) { // checked!
//        //return vnfQueue.indexOf(vnfType) + 1; // index函数从头开始遍历
//        int count = 0;
//        for(Integer vnf : vnfQueue) {
//            if(vnf == vnfType)
//                count++;
//        }
//        return count;
//    }

    private int randomUniformInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }

    public static void verifyKcut() {
        // TODO
    }
}
