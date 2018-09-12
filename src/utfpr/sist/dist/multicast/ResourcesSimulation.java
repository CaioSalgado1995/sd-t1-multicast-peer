package utfpr.sist.dist.multicast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import utfpr.sist.dist.model.KnownPeers;

public class ResourcesSimulation {
	
    public static void main(String[] args) throws InterruptedException {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Boolean flag = true;
        Peer peer;

        try {

            System.out.println("Digite o nome do processo: ");
            String nome = in.readLine();

            peer = new Peer(nome);

            System.out.println("Comunicacao multicast iniciada. \n");
            while (flag) {
                System.out.println("Digite o número da opção que deseja executar:");
                System.out.println("0 - sair");
                System.out.println("1 - listar peers conhecidos");
                System.out.println("2 - solicitar recurso A");
                System.out.println("3 - solicitar recurso B");
                System.out.println("4 - liberar recurso A");
                System.out.println("5 - liberar recurso B");
                System.out.println("6 - meu estado atual recurso A");
                System.out.println("7 - meu estado atual recurso B");
                System.out.println("8 - consultar numero de aprovacoes");
                System.out.println("9 - Verificar fila A");
                System.out.println("10 - Verificar fila B");
                
                int opcao = Integer.parseInt(in.readLine());
                System.out.println(opcao);

                switch (opcao) {
                    case 0:
                    	// manda mensagens para os outros usuários avisando que saiu do grupo
                        peer.getGroupMulticast().sendMulticast(peer.getPeerName() + "-" + "saindo do grupo");
                        System.exit(0);
                        break;
                    case 1:
                    	peer.listKnownPeers();
                        break;                     
                    case 2:
                    	if(!peer.getCurrentStateResourceA().equals("HELD")) {
                    		peer.setRequestResourceA(true);
                        	peer.setCurrentStateResourceA("WANTED");
                        	long time = 0;
                        	if(peer.getTimeRequestResourceA() == 0) {
                        		time = new Date().getTime();
                        		peer.setTimeRequestResourceA(time);
                        	}else {
                        		time = peer.getTimeRequestResourceA();
                        	}
                        	peer.getGroupMulticast().sendMulticast(peer.getPeerName() + "-" + "quero alocar recurso A" + "-" + time);
                        	// espera as respostas dos outros pares
                        	Thread.sleep(10000);
                        	// verifica se alguem morreu
                        	peer.checkIfSomePeerDieResourceA();
                    	}else {
                    		System.out.println("Você já está com o recurso A");
                    	}
                    	break;
                    case 3:
                    	if(!peer.getCurrentStateResourceB().equals("HELD")) {
                    		peer.setRequestResourceB(true);
                        	peer.setCurrentStateResourceB("WANTED");
                        	long time = 0;
                        	if(peer.getTimeRequestResourceB() == 0) {
                        		time = new Date().getTime();
                        		peer.setTimeRequestResourceB(time);
                        	}else {
                        		time = peer.getTimeRequestResourceB();
                        	}
                        	peer.getGroupMulticast().sendMulticast(peer.getPeerName() + "-" + "quero alocar recurso B" + "-" + time);
                        	// espera as respostas dos outros pares
                        	Thread.sleep(10000);
                        	// verifica se alguem morreu
                        	peer.checkIfSomePeerDieResourceB();
                    	}else {
                    		System.out.println("Você já está com o recurso B");
                    	}
                        break;
                    case 4:
                    	peer.setCurrentStateResourceA("RELEASED");
                        break;
                    case 5:
                    	peer.setCurrentStateResourceB("RELEASED");
                    	break;
                    case 6:
                    	System.out.println(peer.getCurrentStateResourceA());
                    	break;
                    case 7:
                    	System.out.println(peer.getCurrentStateResourceB());
                    	break;
                    case 8:
                    	System.out.println(peer.getNumberApprovations());
                    	break;
                    case 9:
                    	System.out.println(peer.getQueueResourceA().toString());
                    	break;
                    case 10:
                    	System.out.println(peer.getQueueResourceB().toString());
                    	break;
                    	
                    default:
                        System.out.println("Opcao invalida! Selecione novamente!");

                }
                System.out.println("\n");
            }
        } catch (IOException e) {
            System.out.println("Main IOException: " + e);
            System.exit(0);
        }
    }
}
 