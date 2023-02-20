package pkg;
//Reham Said 19103134

public class Operation {
	
	private String name;
	private String opcode;
	private int format;

	public Operation(String name, String opcode, int format) {
		super();
		this.name = name;
		this.opcode = opcode;
		this.format = format;
	}

	public int getFormat() {
		return format;
	}

	public void setFormat(int format) {
		this.format = format;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOpcode() {
		return opcode;
	}

	public void setOpcode(String opcode) {
		this.opcode = opcode;
	}

	@Override
	public String toString() {
		return name + "\t" + opcode + "\t" + format;
	}
	
	
	
	
}
