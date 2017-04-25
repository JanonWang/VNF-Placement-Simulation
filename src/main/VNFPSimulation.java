package main;

import algorithms.*;
import manager.NetworkServiceManager;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.io.File;
import java.io.FileWriter;
import java.util.*;


/**
 * Created by Janon Wang on 2016/11/4.
 * 仿真程序的入口，初始化拓扑和NS产生器。两部分的算法都是对这边产生的server，vm，vnf进行操作
 * @author Janon Wang
 */
public class VNFPSimulation {
    public final static int vnfSum = 60; // the total type of the VNF
    // private final static double vnfRelationPara = 0.1;

    private final static double alpha = 2.1;  // positive number!
    private final static double trafficRateMin = 10; // Mbps
    private final static int generateNSTimes = 1;

    private final static int fatTreeK = 10;

    private Set<NetworkService> acceptedNS;
    // private VirtualMachine[] availableVNF; // 用于记录对于每种vnf，目前空闲的VM信息，某一时刻，对于某种VNF，只有一个可行的VNF, VNF的类型即为其数组序号
    private Topology topo;
    private VNFPlacement vnfPlacement;

    private VNFPSimulation(Topology topo, VNFPlacement vnfPlacement) {
        this.acceptedNS = new HashSet<>();
        // this.availableVNF = new VirtualMachine[vnfSum + 1];
        this.topo = topo;
        this.vnfPlacement = vnfPlacement;
    }

    private void showSimulationResult1(double para1, double para2, double para3) {
        String filename = "HeuristicVNFPlacementSameServer" + "-" + para1 + "-" + para2 + "-" + para3 + ".txt";
        File file = new File(filename);
        if(file.exists()) {
            if(!file.delete())
                System.err.println("the result file delete fail");
        } else {
            try{
                if(!file.createNewFile())
                    System.err.println("the result file create fail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try{
            FileWriter out = new FileWriter(file);
            out.write("-------------仿真结果----------- \r\n");
            double acceptNSCount = this.acceptedNS.size();

            out.write("接受的network service总数为：" + acceptNSCount + "\r\n");
//
//            double sfcTotalLength = 0;
//            double nsTotalHops = 0;
//            for(NetworkService s : this.acceptedNS) {
//                sfcTotalLength += s.sfcLength;
//                int nsHops = 0;
//                LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
//                Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
//                Address lastNode = vnfsIterator.next().position;
//                while(vnfsIterator.hasNext()) {
//                    Address thisNode = vnfsIterator.next().position;
//                    nsHops += distanceBetweenTwoServer(lastNode, thisNode);
//                    lastNode = thisNode;
//                }
//                nsTotalHops += nsHops;
//            }
//            double sfcAverLength = sfcTotalLength / acceptNSCount;
//            double nsAverHops = nsTotalHops / acceptNSCount;

//            out.write("接受的network service中服务链的平均长度为：" + sfcAverLength + "\r\n");
//
//            out.write("接受的network service中的平均的跳数为：" + nsAverHops + "\r\n");

//            out.write("vnf count matrix 统计的结果如下：");
//            int[][] vnfCountMatrix = vnfPlacement.getVnfCountMatrix();
//            for(int i = 1; i < VNFPSimulation.vnfSum + 1; i++) {
//                out.write("\r\n     类型为 ***" + i + "*** 的VNF与其他之间的关联性：\r\n         ");
//                for (int j = 1; j < VNFPSimulation.vnfSum + 1; j++) {
//                    out.write("vnf" + j + ": " + vnfCountMatrix[i][j] + "    ");
//                }
//            }

//            out.write("\r\n\r\n\r\n\r\n\r\n\r\n每个pod中不同类型VNF的统计：");
//            for(int i = 1; i <= fatTreeK; i++) {
//                out.write("\r\n\r\n\r\n\r\n     pod " + i + "中的统计情况：\r\n         ");
//                int[] podVnf = topo.getPodVnfCounts(i);
//                for(int j = 1; j <= VNFPSimulation.vnfSum; j++) {
//                    out.write("vnf" + j + "的总数为: " + podVnf[j] + "    ");
//                }
//                out.write("\r\n\r\n         pod中每个EdgeSwitch上不同的VNF的统计：");
//                for(int j = 1; j <= fatTreeK / 2; j++) {
//                    out.write("\r\n             EdgeSwitch" + j + "中的统计情况：\r\n                 ");
//                    int[] edgeVnf = topo.getEdgeVnfCounts(i, j);
//                    for(int k = 1; k <= VNFPSimulation.vnfSum; k++) {
//                        out.write("vnf" + k + "的总数为: " + edgeVnf[k] + "    ");
//                    }
//                }
//            }

            out.write("\r\n\r\n\r\nServer资源使用的情况统计及VM资源使用情况统计：\r\n");
            for(int i = 1; i <= fatTreeK; i++) {
                for(int j = 1; j <= fatTreeK / 2; j++) {
                    for(int k = 1; k <= fatTreeK / 2; k++) {
                        PhysicalServer server = topo.getServerById(i, j, k);
                        out.write("\r\n\r\n" + server.getAddress().toString() + ":\r\n");
                        out.write("     资源剩余： " + "cpu:" + server.getCpuCoreRemain() + "    ram:" + server.getRamRemain() + "\r\n");
                        out.write("不同的VM的统计： \r\n");
                        Set<VirtualMachine> vmSet = server.getVmSet();
                        int vmNo = 1;
                        for(VirtualMachine vm : vmSet) {
                            out.write(vmNo + "号的类型为VNF"+vm.vnfType+"  计算资源剩余：" + vm.cpuRemain + "\r\n");
                            vmNo++;
                        }
                    }
                }
            }
            out.close();
        } catch(Exception e1) {
            e1.printStackTrace();
        }

    }

    private void showSimulationResult2(double para1, double para2, double para3, double vnfRelationPara, int choice) {
        String Algorithmchoice;
        if(choice == 1) {
            Algorithmchoice = "newHeuristic";
        } else if(choice == 2) {
            Algorithmchoice = "newAdvance";
        } else {
            Algorithmchoice = "Random";
        }
        String filename = Algorithmchoice + "-SimulationResult-newNSGenerator-summarize-AllVnfRelationPara(0.20~0.40).txt";

        File file = new File(filename);

        if(para1 == 1 && para2 == 1 && para3 == -1 && vnfRelationPara == 0.2) {
            try{
                if(file.exists()) {
                    if(!file.delete())
                        System.err.println("the result file delete fail");
                }
                if(!file.createNewFile())
                    System.err.println("the result file create fail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try{
            FileWriter out = new FileWriter(file, true);
            out.write("-------------仿真结果----------- \r\n" +
                    "当前的仿真条件为：\r\n" +
                    "fatTreeK: " + fatTreeK  + "    vnfRelationPara: " + vnfRelationPara + "    权值系数为: " + para1 + "  " + para2 + "  " + para3 + "\r\n" +
                    "--------------------------------\r\n");

            double acceptNSCount = this.acceptedNS.size();

            out.write("接受的network service总数为：" + acceptNSCount + "\r\n");

            double sfcTotalLength = 0;
            double nsTotalHops = 0;

            out.write("服务链映射情况具体如下：\r\n");
            for(NetworkService s : this.acceptedNS) {
                sfcTotalLength += s.sfcLength;
                int nsHops = 0;
                LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
                Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
                VirtualNetworkFunction headVNF = vnfsIterator.next();
//                out.write(headVNF.vnfType + "(" + headVNF.position.toString() + ")-(" );
                Address lastNode = headVNF.position;
                int lastType = headVNF.vnfType;
                while(vnfsIterator.hasNext()) {
                    VirtualNetworkFunction thisVNF = vnfsIterator.next();
                    Address thisNode = thisVNF.position;
                    int thisType = thisVNF.vnfType;
//                    String isMostRelated;
//                    if(vnfPlacement.isMostRelated(lastType, thisType)) {
//                        isMostRelated = "紧密";
//                    } else {
//                        isMostRelated = "不紧";
//                    }
                    double thisHop = distanceBetweenTwoServer(lastNode, thisNode);
                    nsHops += thisHop;
//                    out.write(isMostRelated + thisHop + ")-> " + thisVNF.vnfType + "(" + thisVNF.position.toString() + ") -(");
                    lastNode = thisNode;
                    lastType = thisType;
                }
//                out.write(")--> end \r\n\r\n");
                nsTotalHops += nsHops;
            }
            double sfcAverLength = sfcTotalLength / acceptNSCount;
            double nsAverHops = nsTotalHops / acceptNSCount;
            double averHopsBetweenTwoVnf = nsAverHops / (sfcAverLength - 1);

            out.write("接受的network service中服务链的平均长度为：" + sfcAverLength + "\r\n" +
                    "接受的network service中的平均的跳数为：" + nsAverHops + "\r\n");
            out.write("两个vnf之间的平均跳数：" + averHopsBetweenTwoVnf + "\r\n\r\n\r\n");
            out.close();
        } catch(Exception e1) {
            e1.printStackTrace();
        }
    }

    private static void showSimulationResult3(double para1, double para2, double para3, double vnfRelationPara,
                                              int choice, double[] averageHopResult) {
        String Algorithmchoice;
        if(choice == 1) {
            Algorithmchoice = "newHeuristic";
        } else if(choice == 2) {
            Algorithmchoice = "newAdvance";
        } else {
            Algorithmchoice = "Random";
        }
        String filename = Algorithmchoice + "-SimulationResult-newNSGenerator-summarize-AllVnfRelationPara(0.20~0.40)-runTime10.txt";

        File file = new File(filename);

        if(para1 == 1 && para2 == 1 && para3 == -1 && vnfRelationPara == 0.2) {
            try{
                if(file.exists()) {
                    if(!file.delete())
                        System.err.println("the result file delete fail");
                }
                if(!file.createNewFile())
                    System.err.println("the result file create fail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        double sumAverageHop = 0;
        double meanAverageHop;
        for(double var : averageHopResult) {
            sumAverageHop += var;
        }
        meanAverageHop = sumAverageHop/averageHopResult.length;

        try{
            FileWriter out = new FileWriter(file, true);
            out.write("-------------仿真结果----------- \r\n" +
                    "当前的仿真条件为：\r\n" +
                    "fatTreeK: " + fatTreeK  + "    vnfRelationPara: " + vnfRelationPara + "    权值系数为: " + para1 + "  " + para2 + "  " + para3 + "\r\n" +
                    "--------------------------------\r\n");
            out.write("运行多次之后每次的结果为：\r\n");
            for(double var : averageHopResult) {
                out.write(var + "\r\n");
            }
            out.write("运行多次之后的结果取平均，两个vnf之间的平均跳数：" + meanAverageHop + "\r\n\r\n\r\n");
            out.close();
        } catch(Exception e1) {
            e1.printStackTrace();
        }
    }

    private double getAverageHops() {
        double acceptNSCount = this.acceptedNS.size();
        double sfcTotalLength = 0;
        double nsTotalHops = 0;
        for(NetworkService s : this.acceptedNS) {
            sfcTotalLength += s.sfcLength;
            int nsHops = 0;
            LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
            Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
            VirtualNetworkFunction headVNF = vnfsIterator.next();
            Address lastNode = headVNF.position;
            while(vnfsIterator.hasNext()) {
                VirtualNetworkFunction thisVNF = vnfsIterator.next();
                Address thisNode = thisVNF.position;
                double thisHop = distanceBetweenTwoServer(lastNode, thisNode);
                nsHops += thisHop;
                lastNode = thisNode;
            }
            nsTotalHops += nsHops;
        }
        double sfcAverLength = sfcTotalLength / acceptNSCount;
        double nsAverHops = nsTotalHops / acceptNSCount;
        return nsAverHops / (sfcAverLength - 1);
    }

    private int distanceBetweenTwoServer(Address addr1, Address addr2) {
        boolean isSamePod = (addr1.getPod() == addr2.getPod());
        boolean isSameEdge = (addr1.getEdge() == addr2.getEdge());
        boolean isSameServer = (addr1.getServer() == addr2.getServer());
        if(isSamePod) {
            if(isSameEdge) {
                if(isSameServer) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                return 3;
            }
        } else {
            return 5;
        }
    }

    private double getAverageLinkBW() {
        double acceptNSCount = this.acceptedNS.size();
        double nsTotalLinkConsume = 0;
        for(NetworkService s : this.acceptedNS) {
            int nsHops = 0;
            int trafficRate = s.trafficRate;
            LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
            Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
            VirtualNetworkFunction headVNF = vnfsIterator.next();
            Address lastNode = headVNF.position;
            while(vnfsIterator.hasNext()) {
                VirtualNetworkFunction thisVNF = vnfsIterator.next();
                Address thisNode = thisVNF.position;
                double thisHop = distanceBetweenTwoServer(lastNode, thisNode);
                nsHops += thisHop;
                lastNode = thisNode;
            }
            nsTotalLinkConsume += nsHops*trafficRate;
        }
        return nsTotalLinkConsume / acceptNSCount;
    }

    private double getAverageVMUtility() {
        int k = VNFPSimulation.fatTreeK;
        double VMCount = 0;
        double VMTotalUsedCpuCapacity = 0;
        for(short i = 1; i <= k; i++) {
            PhysicalServer[][] physicalServers = topo.getServerByPod(i);
            for(int j = 1; j <= k / 2; j++) {
                for(int h = 1; h <= k / 2; h++) {
                    Set<VirtualMachine> vmSet = physicalServers[j][h].getVmSet();
                    for(VirtualMachine vm : vmSet) {
                        VMTotalUsedCpuCapacity += (vm.cpuCapacity - vm.cpuRemain);
                        VMCount++;
                    }
                }
            }
        }
        return VMTotalUsedCpuCapacity/(VMCount*10000);
    }

    private static VNFPSimulation runOnce(double para1, double para2, double para3, double vnfRelationPara, int choice) {
        FatTreeTopo topo = new FatTreeTopo(fatTreeK);
        NetworkServiceManager networkServiceManager = new NetworkServiceManager(vnfRelationPara,
                alpha, trafficRateMin, generateNSTimes);
        VNFPlacement vnfPlacement;
        if(choice == 1) {
            vnfPlacement = new HeuristicVNFPlacementSameRack(topo, para1, para2, para3);
        } else if (choice == 2) {
            vnfPlacement = new HeuristicVNFPlacementWithClustering(topo, para1, para2, para3);
        } else if (choice == 3) {
            vnfPlacement = new HeuristicVNFPlacementSameServer(topo, para1, para2, para3);
        } else if(choice == 4) {
            vnfPlacement = new GreedyLeastLoadVNFPlacement(topo, para1, para2, para3);
        }
        else {
            System.err.println("input error, invalid choice");
            return null;
        }

        VNFPSimulation vnfpSimulation = new VNFPSimulation(topo, vnfPlacement);
        boolean ifContinue = true;

        while(ifContinue) {
            NetworkService ns = networkServiceManager.nextNS();
            if(ns == null) {
                break;
            }
//            System.out.print("产生的服务链为：");
//            for(VirtualNetworkFunction v : ns.sfcList) {
//                System.out.print(v.vnfType + "-->");
//            }
//            System.out.print("end\n");
            vnfPlacement.countVnf(ns);
            Iterator<VirtualNetworkFunction> sfcIterator = ns.sfcList.iterator();
            VirtualNetworkFunction headVnf = sfcIterator.next();
            int lastVnfType = headVnf.vnfType;
            Address lastVNFServer;
            // 处理服务链的第一个VNF
//            System.out.print("头VNF -- ");
            VirtualMachine vm = vnfPlacement.placeVNFInTopo(headVnf);
            if(vm == null) {
                break;
            }
            vm.launchVnf(headVnf);
//            System.out.println(headVnf.vnfType + " is launched at " + vm.position.getAddress().toString());
            lastVNFServer = vm.position.getAddress();
            while(sfcIterator.hasNext()) {
                VirtualNetworkFunction thisVNF = sfcIterator.next();
                if(vnfPlacement.isMostRelated(lastVnfType, thisVNF.vnfType)) {
                    // 上一跳和这一跳的关系紧密
//                    System.out.print("mostRelated -- ");
                    vm = vnfPlacement.placeVNFInServer(thisVNF, lastVNFServer);
                    if(vm == null) {
                        ifContinue = false;
                        break;
                    }
                    vm.launchVnf(thisVNF);
//                    System.out.println(thisVNF.vnfType + " is launched at " + vm.position.getAddress().toString());
                    lastVNFServer = vm.position.getAddress();
                    lastVnfType = thisVNF.vnfType;
                }
                else if(vnfPlacement.inSameGroup(thisVNF.vnfType, lastVnfType)) {
                    // 上一跳和这一跳在同一组中
//                    System.out.print("sameGroup -- ");
                    vm = vnfPlacement.placeVNFInRack(thisVNF, lastVNFServer);
                    if(vm == null) {
                        ifContinue = false;
                        break;
                    }
                    vm.launchVnf(thisVNF);
//                    System.out.println(thisVNF.vnfType + " is launched at " + vm.position.getAddress().toString());
                    lastVNFServer = vm.position.getAddress();
                    lastVnfType = thisVNF.vnfType;
                }
                else {
                    // 上一跳和这一跳不在同一组中，关系也不紧密
//                    System.out.print("noRelation -- ");
                    vm = vnfPlacement.placeVNFInTopo(thisVNF);
                    if(vm == null) {
                        ifContinue = false;
                        break;
                    }
                    vm.launchVnf(thisVNF);
//                    System.out.println(thisVNF.vnfType + " is launched at " + vm.position.getAddress().toString());
                    lastVNFServer = vm.position.getAddress();
                    lastVnfType = thisVNF.vnfType;
                }
            }
            if(ifContinue) {
                vnfpSimulation.acceptedNS.add(ns);
            }
        }
//        vnfpSimulation.showSimulationResult1(para1, para2, para3);
        return vnfpSimulation;
//        vnfpSimulation.showSimulationResult2(para1, para2, para3, vnfRelationPara, choice);
        // vnfpSimulation.showSimulationResult1(para1, para2, para3);
    }


    public static void main(String[] args) {

//        System.out.println("输入1为Same rack heuristic，输入2为heuristic with clustering\n输入3为Same server heuristic, 输入4为GLL");
//        int input;
//        while(true) {
//            int s = 0;
//            try{
//                s = System.in.read();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            if(s == 49) {
//                input = 1;
//                break;
//            }
//            if(s == 50) {
//                input = 2;
//                break;
//            }
//            if(s == 51) {
//                input = 3;
//                break;
//            }
//            if(s == 52) {
//                input = 4;
//                break;
//            }
//        }

        for(int choice = 1; choice <= 4; choice++) {
            switch (choice){
                case 1:
                    System.out.println("选择的算法为SRH");
                    break;
                case 2:
                    System.out.println("选择的算法为Ours");
                    break;
                case 3:
                    System.out.println("选择的算法为SSH");
                    break;
                case 4:
                    System.out.println("选择的算法为GLL");
                    break;
            }
            int input = choice;
            for (int i = 5; i <= 5; i++) {
                double vnfRelationPara = (double) i / 10;

                double para1 = 1;
                double para2 = 1;
                double para3 = -1;

                System.out.println("-----仿真程序开始-----");
                System.out.println("权值系数为：" + para1 + "   " + para2 + "   " + para3 + "   " + "vnfRelation系数为：" + vnfRelationPara);

                int runTimes = 10;
                double[] acceptedNSCounts = new double[runTimes];
                double[] averageHops = new double[runTimes];
                double[] averageLinkBWs = new double[runTimes];
                double[] averageVMUtilities = new double[runTimes];
                VNFPSimulation vnfpSimulation = null;
                for (int j = 0; j < runTimes; j++) {
                    vnfpSimulation = VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara, input);
                    if (vnfpSimulation != null) {
                        averageHops[j] = vnfpSimulation.getAverageHops();
                        averageLinkBWs[j] = vnfpSimulation.getAverageLinkBW();
                        averageVMUtilities[j] = vnfpSimulation.getAverageVMUtility();
                        acceptedNSCounts[j] = vnfpSimulation.acceptedNS.size();
                    }
                }

                double hopSum = 0;
                double linkBWSum = 0;
                double VMUtilitySum = 0;
                double acceptedNSCountSum = 0;
                for (double averageHop : averageHops) {
                    hopSum += averageHop;
                }
                for (double averageLinkBW : averageLinkBWs) {
                    linkBWSum += averageLinkBW;
                }
                for (double averageVMUtility : averageVMUtilities) {
                    VMUtilitySum += averageVMUtility;
                }
                for (double acceptedNSCount : acceptedNSCounts) {
                    acceptedNSCountSum += acceptedNSCount;
                }
                System.out.println("-----打印数据-----");
                if (vnfpSimulation != null) {
                    System.out.println("接受的NS总数：" + acceptedNSCountSum / runTimes);
//                    System.out.println("平均跳数：" + hopSum / runTimes);
                    System.out.println("每个NS平均带宽占用：" + linkBWSum / runTimes + "MB");
//                    System.out.println("平均的VM资源利用率：" + VMUtilitySum / runTimes);
                    System.out.println();
                    System.out.println();
                }

//            VNFPSimulation.showSimulationResult3(para1, para2, para3, vnfRelationPara, input, averageHopResult);


//            para3 = 0.5;
//            VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara,input);
//
//            para3 = 1;
//            VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara,input);
//
//            para3 = 1.5;
//            VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara,input);
//
//            para1 = 0;
//            para3 = 1;
//            VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara,input);

            }
        }
    }
}
