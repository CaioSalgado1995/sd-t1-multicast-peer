package utfpr.sist.dist.multicast;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;

import utfpr.sist.dist.model.KnownPeers;
import utfpr.sist.dist.multicast.Criptografia;
import utfpr.sist.dist.multicast.Multicast;
import utfpr.sist.dist.util.Util;

public class Peer {
	
	private final Multicast groupMulticast;
    private final String peerName;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private List<KnownPeers> knownPeers;
    private List<KnownPeers> responsePeersA;
    private List<KnownPeers> responsePeersB;
    private Queue<Request> queueResourceA;
    private Queue<Request> queueResourceB;
    private boolean requestResourceA = false;
    private boolean requestResourceB = false;
    private int numberApprovations = 0;
    private String currentStateResourceA = "RELEASED";
    private String currentStateResourceB = "RELEASED";
    private long timeRequestResourceA = 0;
    private long timeRequestResourceB = 0;
    
    public Peer(String nome) throws IOException {

        this.peerName = nome;

        // Create and start the multiCast group, and send a message to others peers
        this.groupMulticast = new Multicast(this, Util.IP_GROUP, 6789);
        this.groupMulticast.setName(nome);
        this.groupMulticast.start();
        this.knownPeers = new ArrayList<KnownPeers>();
        this.responsePeersA = new ArrayList<KnownPeers>();
        this.responsePeersB = new ArrayList<KnownPeers>();
        this.queueResourceA = new LinkedList<Request>();
        this.queueResourceB = new LinkedList<Request>();

        // Create public and private keys
        Map<String, Object> keys = null;
        try {
            keys = Criptografia.getRSAKeys();
        } catch (Exception ex) {
        	// If fail in generate some key the process is finished
            System.exit(0);
        }
        this.privateKey = (PrivateKey) keys.get(Util.PRIVATE_KEY);
        this.publicKey = (PublicKey) keys.get(Util.PUBLIC_KEY);
        String chavePublica = convertPublicKeyToString(publicKey);
        
        sendHelloMessage(chavePublica);
    }
    
    private void sendHelloMessage(String chavePublica) throws IOException{
    	this.groupMulticast.sendMulticast(
        		this.peerName + 
        		Util.MESSAGE_DIVISOR + 
        		Util.MESSAGE_GET_IN + 
        		Util.MESSAGE_DIVISOR + 
        		chavePublica);
    }
    
    public void addKnownPeer(KnownPeers peer) {
        knownPeers.add(peer);
    }
    
    public void removeKnownPeer(KnownPeers peer) {
    	knownPeers.remove(peer);
    }
    
    public void tryAddMissingPeer(KnownPeers knownPeer) {
		if(!knownPeers.contains(knownPeer)) {
			knownPeers.add(knownPeer);
		}
	}
    
    public void checkIfSomePeerDieResourceA() {
    	
    	if(!this.getCurrentStateResourceA().equals("HELD")) {
    		List<KnownPeers> diedPeers = new ArrayList<KnownPeers>();
        	int numberPeersDied = 0;
        	
        	for (KnownPeers knownPeer : this.knownPeers) {
    			if(!this.responsePeersA.contains(knownPeer)) {
    				// this means that peer die, so we have to check if we have the number of approvals to
    				// get the resource is enough
    				diedPeers.add(new KnownPeers(knownPeer.getName()));
    				numberPeersDied ++;
    			}
    		}
        	
        	if(numberPeersDied > 0) {
        		
        		for (KnownPeers knownPeer : diedPeers) {
    				removeKnownPeer(knownPeer);
				}
        		
        		if(this.numberApprovations == this.knownPeers.size()) {
            		this.setCurrentStateResourceA("HELD");
        		}
        		// avisa os outros recursos cada peer que "morreu"
        		try {
        			for (KnownPeers knownPeer : diedPeers) {
        				this.groupMulticast.sendMulticast(this.peerName + "-" + "esse peer morreu" + "-" + knownPeer.getName());
    				}
    			} catch (IOException e) {
    				System.out.println("Erro de comunicação");
    			}
        	}	
    	}
 
    	this.numberApprovations = 0;
    	this.requestResourceA = false;
    }
    
    public void checkIfSomePeerDieResourceB() {
    	
    	if(!this.getCurrentStateResourceB().equals("HELD")){
    		List<KnownPeers> diedPeers = new ArrayList<KnownPeers>();
        	int numberPeersDied = 0;
        	
        	for (KnownPeers knownPeer : this.knownPeers) {
    			if(!this.responsePeersB.contains(knownPeer)) {
    				diedPeers.add(new KnownPeers(knownPeer.getName()));
    				numberPeersDied ++;
    			}
    		}
        	
        	if(numberPeersDied > 0) {
        		
        		for (KnownPeers knownPeer : diedPeers) {
    				removeKnownPeer(knownPeer);
				}
        		
        		if(this.numberApprovations == this.knownPeers.size()) {
        			this.setCurrentStateResourceB("HELD");
        		}
        		// avisa os outros recursos cada peer que "morreu"
        		try {
        			for (KnownPeers knownPeer : diedPeers) {
        				removeKnownPeer(knownPeer);
        				this.groupMulticast.sendMulticast(this.peerName + "-" + "esse peer morreu" + "-" + knownPeer.getName());
    				}
    			} catch (IOException e) {
    				System.out.println("Erro de comunicação");
    			}
        	}
    	}
    	
    	this.numberApprovations = 0;
    	this.requestResourceB = false;
    	
    }
    
    public void listKnownPeers() {
        if (!knownPeers.isEmpty()) {
            System.out.println("\nOs peers conhecidos por este peer são: [nomePeer][chavePublica]");
            
            for (KnownPeers peer : knownPeers) {
            	String[] publicKeyParts = peer.getPublicKey().split("\n");
            	PublicKey publicKey = Peer.getPublicKeyFromString(publicKeyParts[0], publicKeyParts[1]);
            	if(publicKey != null) {
            		System.out.println("[" + peer.getName() + "][" + publicKey.toString() + "]");
            	}
            	
			}
            
        } else {
            System.out.println("Este peer não possui peers conhecidos");
        }
    }
    
    public static String convertPublicKeyToString(PublicKey chavePublica) {
        KeyFactory fact;
        RSAPublicKeySpec pub = null;
        String m = "";
        String e = "";
        try {
            fact = KeyFactory.getInstance("RSA");
            pub = fact.getKeySpec(chavePublica, RSAPublicKeySpec.class);
            m = pub.getModulus().toString();
            e = pub.getPublicExponent().toString();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return m + "\n" + e;
    }
    
    public static PublicKey getPublicKeyFromString(String modulus, String exponent) {
        BigInteger chave = new BigInteger(modulus);
        BigInteger e = new BigInteger(exponent);
        RSAPublicKeySpec keySpec;
        keySpec = new RSAPublicKeySpec(chave, e);
        KeyFactory fact;
        try {
            fact = KeyFactory.getInstance("RSA");
            return fact.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public Multicast getGroupMulticast() {
		return groupMulticast;
	}

	public String getPeerName() {
		return peerName;
	}

	public List<KnownPeers> getKnownPeers() {
		return knownPeers;
	}

	public void setKnownPeers(List<KnownPeers> knownPeers) {
		this.knownPeers = knownPeers;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}
    
    public PublicKey getPublicKey() {
		return publicKey;
	}

	public Queue<Request> getQueueResourceA() {
		return queueResourceA;
	}

	public void setQueueResourceA(Queue<Request> queueResourceA) {
		this.queueResourceA = queueResourceA;
	}

	public Queue<Request> getQueueResourceB() {
		return queueResourceB;
	}

	public void setQueueResourceB(Queue<Request> queueResourceB) {
		this.queueResourceB = queueResourceB;
	}

	public boolean isRequestResourceA() {
		return requestResourceA;
	}

	public void setRequestResourceA(boolean requestResourceA) {
		this.requestResourceA = requestResourceA;
	}

	public boolean isRequestResourceB() {
		return requestResourceB;
	}

	public void setRequestResourceB(boolean requestResourceB) {
		this.requestResourceB = requestResourceB;
	}

	public int getNumberApprovations() {
		return numberApprovations;
	}

	public void setNumberApprovations(int numberApprovations) {
		this.numberApprovations = numberApprovations;
	}

	public String getCurrentStateResourceA() {
		return currentStateResourceA;
	}

	public void setCurrentStateResourceA(String currentStateResourceA) {
		this.currentStateResourceA = currentStateResourceA;
	}

	public String getCurrentStateResourceB() {
		return currentStateResourceB;
	}

	public void setCurrentStateResourceB(String currentStateResourceB) {
		this.currentStateResourceB = currentStateResourceB;
	}

	public List<KnownPeers> getResponsePeersA() {
		return responsePeersA;
	}

	public void setResponsePeersA(List<KnownPeers> responsePeersA) {
		this.responsePeersA = responsePeersA;
	}

	public List<KnownPeers> getResponsePeersB() {
		return responsePeersB;
	}

	public void setResponsePeersB(List<KnownPeers> responsePeersB) {
		this.responsePeersB = responsePeersB;
	}

	public long getTimeRequestResourceA() {
		return timeRequestResourceA;
	}

	public void setTimeRequestResourceA(long timeRequestResourceA) {
		this.timeRequestResourceA = timeRequestResourceA;
	}

	public long getTimeRequestResourceB() {
		return timeRequestResourceB;
	}

	public void setTimeRequestResourceB(long timeRequestResourceB) {
		this.timeRequestResourceB = timeRequestResourceB;
	}
}
