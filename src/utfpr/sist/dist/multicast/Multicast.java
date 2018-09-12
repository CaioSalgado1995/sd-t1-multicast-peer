package utfpr.sist.dist.multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import utfpr.sist.dist.model.KnownPeers;
import utfpr.sist.dist.util.Util;

//TODO implement logger otherwise System.out to log exception messages
public class Multicast extends Thread {

	private boolean executa = true;
	private final MulticastSocket socketMulticast;
	private final int multicastPort;
	private final InetAddress group;
	private final Peer peer;
	
	public Multicast(Peer peer, String host, int multicastPort) throws IOException {
		this.group = InetAddress.getByName(host);
		this.multicastPort = multicastPort;
		this.socketMulticast = new MulticastSocket(multicastPort);
		this.socketMulticast.joinGroup(group);
		this.socketMulticast.setTimeToLive(2);
		this.peer = peer;
	}
	
	@Override
	public void run() {

		while(executa) {
			DatagramPacket d = new DatagramPacket(new byte[256], 256);
            try {
                socketMulticast.receive(d);
            } catch (IOException ex) {
                System.out.println("Multicast Run: erro ao tentar receber datagrama" + ex);
            }
            String mensagem = new String(d.getData());
            try {
				tratarMensagens(mensagem);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("Erro ao analisar tarefas" + e);
			}
		}
		
		try {
            socketMulticast.leaveGroup(group);
            socketMulticast.close();
        } catch (IOException ex) {
            System.out.println("Erro ao tentar fechar multicast" + ex.getMessage());
        }
	}
	
    public void sendMulticast(String mensagem) throws IOException {
        byte[] data = mensagem.getBytes();
        DatagramPacket d = new DatagramPacket(data, data.length, group, multicastPort);
        socketMulticast.send(d);
    }
    
    public void tratarMensagens(String mensagem) throws IOException { 
        // Base message
    	//[0]-[1]-[2]   
        //[processName]-[message]-[publicKey]

        String[] mensagemDividida = mensagem.split("-");
        
        // close multiCast connection of this peer in case of leaving the group
        if (mensagemDividida[0].trim().equals(this.getName()) && mensagemDividida[1].trim().equals("saindo do grupo")) {
            fecharMulticast();
        }

        // read only messages from a different peer
        if (!mensagemDividida[0].equals(this.getName())) {
            
        	if(mensagemDividida[1].contains("saindo do grupo")) {
        		// remove peer from list of known peers
        		KnownPeers knownPeer = new KnownPeers();
        		knownPeer.setName(mensagemDividida[0].trim());
        		this.peer.removeKnownPeer(knownPeer);
        	}
        	
            // check if a new peer get into the group
            if (mensagemDividida[1].contains("entrou no grupo")) {
                
                // add peer to knows peer
                KnownPeers knownPeer = new KnownPeers();
                knownPeer.setName(mensagemDividida[0].trim());
                knownPeer.setPublicKey(mensagemDividida[2].trim());
                
                this.peer.addKnownPeer(knownPeer);
                
                // configure the message to send to other peers
                String msgEnvio = montaMensagemJaEstouNoGrupo();
                try {
					sendMulticast(msgEnvio);
				} catch (IOException e) {
					System.out.println("Erro ao enviar");
				}
            }
            
            // check other peer response to add to the list of known peers
            if(mensagemDividida[1].trim().contains("ja estou no grupo")) {
            	KnownPeers knownPeer = new KnownPeers();
            	knownPeer.setName(mensagemDividida[0].trim());
            	knownPeer.setPublicKey(mensagemDividida[2].trim());
            	
            	this.peer.tryAddMissingPeer(knownPeer);
            }
            
            // check if the peer want to allocate resource A
            if (mensagemDividida[1].trim().contains("quero alocar recurso A")) {
            	//[0]-[1]-[2]-[3]   
                //[processName]-[message]-[timeToWait]
            	String processRequestName = mensagemDividida[0].trim();
            	long processRequestTime = Long.parseLong(mensagemDividida[2].trim());
            	
            	
            	if(this.peer.getCurrentStateResourceA().equals("RELEASED")) {
            		if(this.peer.getQueueResourceA().isEmpty()) {
            			// se não está comigo e não tem ninguem na minha fila: recurso liberado
            			String msgEnvio = this.peer.getPeerName() + "-" + "recurso A liberado";
            			sendMulticast(msgEnvio);
            		}else {
            			// se tem alguém na fila alguém requisitou antes que você
            			// checa a cabeca da fila sem retirar para verificar se é o peer que requisitou
            			if(this.peer.getQueueResourceA().peek().getName().equals(processRequestName)) {
            				Request removedPeerQueue = this.peer.getQueueResourceA().poll();
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso A liberado";
            				sendMulticast(msgEnvio);
            			}else {
            				// adiciona o novo peer na fila e manda mensagem dizendo que o recurso esta ocupado
            				Request newPeerQueue = new Request(processRequestName, processRequestTime);
            				// adiciona na fila se já não está na fila
            				if(!this.peer.getQueueResourceA().contains(newPeerQueue)) {
            					this.peer.getQueueResourceA().add(newPeerQueue);
            				}
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso A ocupado";
            				sendMulticast(msgEnvio);
            			}
            			
            		}
            	}else if(this.peer.getCurrentStateResourceA().equals("WANTED")) {
            		// se não se meu estado indica que eu QUERO o recurso
            		// se o tempo que eu requisitei é menor que o tempo do recurso que pediu acesso
            		if(this.peer.getTimeRequestResourceA() < processRequestTime) {
            			Request newPeerQueue = new Request(processRequestName, processRequestTime);
            			// adiciona na fila se já não está na fila
            			if(!this.peer.getQueueResourceA().contains(newPeerQueue)) {
        					this.peer.getQueueResourceA().add(newPeerQueue);
        				}
            			// recurso ocupado pois tenho prioridade sobre a zona crítica
            			String msgEnvio = this.peer.getPeerName() + "-" + "recurso A ocupado";
            			sendMulticast(msgEnvio);
            		}else {
            			// se meu tempo é maior, então verifico a fila
            			// se a fila for vazia
            			if(this.peer.getQueueResourceA().isEmpty()) {
            				// recurso liberado
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso A liberado";
            				sendMulticast(msgEnvio);
            			}else {
            				// se existe alguem na fila
            				// checo a cabeca da fila sem remover para verificar se não é o usuário que requisitou
            				if(this.peer.getQueueResourceA().peek().getName().equals(processRequestName)) {
            					// recurso liberado
                				// retira o processo da fila definitivamente
            					Request removedPeerQueue = this.peer.getQueueResourceA().poll();
            					String msgEnvio = this.peer.getPeerName() + "-" + "recurso A liberado";
                				sendMulticast(msgEnvio);
            				}else {
            					if(this.peer.getQueueResourceA().peek().getTime() < processRequestTime) {
            						Request newPeerQueue = new Request(processRequestName, processRequestTime);
            						// adiciona na fila se já não está na fila
            						if(!this.peer.getQueueResourceA().contains(newPeerQueue)) {
                    					this.peer.getQueueResourceA().add(newPeerQueue);
                    				}
            						String msgEnvio = this.peer.getPeerName() + "-" + "recurso A ocupado";
            						sendMulticast(msgEnvio);
            					}
            				}
            			}
            		}
            	}else {
            		Request newPeerQueue = new Request(processRequestName, processRequestTime);
            		// adiciona na fila se já não está na fila
					if(!this.peer.getQueueResourceA().contains(newPeerQueue)) {
    					this.peer.getQueueResourceA().add(newPeerQueue);
    				}
            		// se não significa que eu estou com recurso no estado HELD
            		String msgEnvio = this.peer.getPeerName() + "-" + "recurso A ocupado";
            		sendMulticast(msgEnvio);
            		
            	}
            }
            
            if(mensagemDividida[1].trim().contains("recurso A liberado")) {
            	this.peer.getResponsePeersA().add(new KnownPeers(mensagemDividida[0]));
            	if(this.peer.isRequestResourceA()) {
            		this.peer.setNumberApprovations(this.peer.getNumberApprovations() + 1);
            		if(this.peer.getNumberApprovations() >= this.peer.getKnownPeers().size()) {
            			// uma vez que obtive o recurso seto o status para HELD e o tempo requisitado para 0
            			this.peer.setRequestResourceA(false);
            			this.peer.setTimeRequestResourceA(0);
            			this.peer.setNumberApprovations(0);
            			this.peer.setCurrentStateResourceA("HELD");
            		}
            	}
            }
            
            if(mensagemDividida[1].trim().contains("recurso A ocupado")) {
            	this.peer.getResponsePeersA().add(new KnownPeers(mensagemDividida[0]));
            }
            
            // verifica se pares querem acessar recurso B
            if (mensagemDividida[1].trim().contains("quero alocar recurso B")) {
            	//[0]-[1]-[2]-[3]   
                //[processName]-[message]-[timeToWait]
            	String processRequestName = mensagemDividida[0].trim();
            	long processRequestTime = Long.parseLong(mensagemDividida[2].trim());
            	
            	
            	if(this.peer.getCurrentStateResourceB().equals("RELEASED")) {
            		if(this.peer.getQueueResourceB().isEmpty()) {
            			// se não está comigo e não tem ninguem na minha fila: recurso liberado
            			String msgEnvio = this.peer.getPeerName() + "-" + "recurso B liberado";
            			sendMulticast(msgEnvio);
            		}else {
            			// se tem alguém na fila alguém requisitou antes que você
            			// checa a cabeca da fila sem retirar para verificar se é o peer que requisitou
            			if(this.peer.getQueueResourceB().peek().getName().equals(processRequestName)) {
            				Request removedPeerQueue = this.peer.getQueueResourceB().poll();
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso B liberado";
            				sendMulticast(msgEnvio);
            			}else {
            				// adiciona o novo peer na fila e manda mensagem dizendo que o recurso esta ocupado
            				Request newPeerQueue = new Request(processRequestName, processRequestTime);
            				// adiciona na fila se já não está na fila
            				if(!this.peer.getQueueResourceB().contains(newPeerQueue)) {
            					this.peer.getQueueResourceB().add(newPeerQueue);
            				}
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso B ocupado";
            				sendMulticast(msgEnvio);
            			}
            			
            		}
            	}else if(this.peer.getCurrentStateResourceB().equals("WANTED")) {
            		// se não se meu estado indica que eu QUERO o recurso
            		// se o tempo que eu requisitei é menor que o tempo do recurso que pediu acesso
            		if(this.peer.getTimeRequestResourceB() < processRequestTime) {
            			Request newPeerQueue = new Request(processRequestName, processRequestTime);
            			// adiciona na fila se já não está na fila
            			if(!this.peer.getQueueResourceB().contains(newPeerQueue)) {
        					this.peer.getQueueResourceB().add(newPeerQueue);
        				}
            			// recurso ocupado pois tenho prioridade sobre a zona crítica
            			String msgEnvio = this.peer.getPeerName() + "-" + "recurso B ocupado";
            			sendMulticast(msgEnvio);
            		}else {
            			// se meu tempo é maior, então verifico a fila
            			// se a fila for vazia
            			if(this.peer.getQueueResourceB().isEmpty()) {
            				// recurso liberado
            				String msgEnvio = this.peer.getPeerName() + "-" + "recurso B liberado";
            				sendMulticast(msgEnvio);
            			}else {
            				// se existe alguem na fila
            				// checo a cabeca da fila sem remover para verificar se não é o usuário que requisitou
            				if(this.peer.getQueueResourceB().peek().getName().equals(processRequestName)) {
            					// recurso liberado
                				// retira o processo da fila definitivamente
            					Request removedPeerQueue = this.peer.getQueueResourceB().poll();
            					String msgEnvio = this.peer.getPeerName() + "-" + "recurso B liberado";
                				sendMulticast(msgEnvio);
            				}else {
            					if(this.peer.getQueueResourceB().peek().getTime() < processRequestTime) {
            						Request newPeerQueue = new Request(processRequestName, processRequestTime);
            						// adiciona na fila se já não está na fila
            						if(!this.peer.getQueueResourceB().contains(newPeerQueue)) {
                    					this.peer.getQueueResourceB().add(newPeerQueue);
                    				}
            						String msgEnvio = this.peer.getPeerName() + "-" + "recurso B ocupado";
            						sendMulticast(msgEnvio);
            					}
            				}
            			}
            		}
            	}else {
            		Request newPeerQueue = new Request(processRequestName, processRequestTime);
            		// adiciona na fila se já não está na fila
					if(!this.peer.getQueueResourceB().contains(newPeerQueue)) {
    					this.peer.getQueueResourceB().add(newPeerQueue);
    				}
            		// se não significa que eu estou com recurso no estado HELD
            		String msgEnvio = this.peer.getPeerName() + "-" + "recurso B ocupado";
            		sendMulticast(msgEnvio);
            		
            	}
            }
            
            if(mensagemDividida[1].trim().contains("recurso B liberado")) {
            	this.peer.getResponsePeersB().add(new KnownPeers(mensagemDividida[0]));
            	if(this.peer.isRequestResourceB()) {
            		this.peer.setNumberApprovations(this.peer.getNumberApprovations() + 1);
            		if(this.peer.getNumberApprovations() >= this.peer.getKnownPeers().size()) {
            			// uma vez que obtive o recurso seto o status para HELD e o tempo requisitado para 0
            			this.peer.setRequestResourceB(false);
            			this.peer.setTimeRequestResourceB(0);
            			this.peer.setNumberApprovations(0);
            			this.peer.setCurrentStateResourceB("HELD");
            		}
            	}
            }
            
            if(mensagemDividida[1].trim().contains("recurso B ocupado")) {
            	this.peer.getResponsePeersB().add(new KnownPeers(mensagemDividida[0]));
            }
            
            // atualiza a lista de pares conhecidos
            if(mensagemDividida[1].trim().contains("esse peer morreu")) {
            	KnownPeers diedPeer = new KnownPeers(mensagemDividida[2].trim());
            	if(this.peer.getKnownPeers().contains(diedPeer)) {
            		this.peer.removeKnownPeer(diedPeer);
            	}
            }
        }
    }
    
    /**
     * Método utilizado para fechar a conexão multicast
     */
    public void fecharMulticast() {
        this.socketMulticast.disconnect();
        this.executa = false;
    }
    
    /*
     * Método utilizado para construir mensagem "já estou no grupo"
     * */
    private String montaMensagemJaEstouNoGrupo(){
    	return this.peer.getPeerName() + "-" + Util.MESSAGE_I_AM_IN_TOO + "-" + 
    			Peer.convertPublicKeyToString(this.peer.getPublicKey());
    }
	
}
