package pkg;
//Reham Said 19103134
import static pkg.Util.*;
import static java.lang.Integer.parseInt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SicXe {

	private String locationCounter;
	private String startingAddress = "000000";
	private String programLength = "000000";
	private boolean baseFlag;
	private int baseValue;
	private final HashMap<String, Operation> opTable;
	private final HashMap<String, String> symbolTable;
	private final HashMap<String, String> registers;
	private final ArrayList<SicLine> instructionLines;
	private final ArrayList<String> errors;
	private final ArrayList<String> directives;

	public SicXe(final String operationsFile) throws FileNotFoundException {
		baseFlag = false;
		baseValue = 0;
		locationCounter = "0000";
		opTable = new HashMap<>();
		symbolTable = new HashMap<>();
		instructionLines = new ArrayList<>();
		errors = new ArrayList<>();
		directives = new ArrayList<>(Arrays.asList("START", "BASE", "END", "WORD", "RESB", "RESW", "BYTE"));
		registers = new HashMap<String, String>(
				Map.of("A", "0", "X", "1", "L", "2", "B", "3", "S", "4", "T", "5", "F", "6", "PC", "8", "SW", "9"));
		fillOptab(operationsFile);
	}

	private void printOpTable() {
		System.out.println("------------------");
		System.out.println("Operation Table:");
		System.out.println("Name" + "\t" + "Opcode" + "\t" + "Format");
		opTable.values().forEach(System.out::println);
		System.out.println("------------------");
	}

	private void printSymbolTable() {
		System.out.println("------------------");
		System.out.println("Symbol Table:");
		System.out.println("Label" + "\t" + "Location");
		for (Map.Entry<String, String> entry : symbolTable.entrySet()) {
			System.out.println(entry.getKey() + "\t" + entry.getValue());
		}
		System.out.println("------------------");
	}

	private void fillOptab(final String operationsFile) throws FileNotFoundException {
		Scanner s = new Scanner(new File(operationsFile));
		while (s.hasNext()) {
			String line = s.nextLine();
			String[] text = line.split("\t");
			String name = text[0];
			String opcode = text[1];
			int format = parseInt(text[2]);
			Operation operation = new Operation(name, opcode, format);
			opTable.put(name, operation);
		}
	}

	private int calculateIncrementValue(String instruction, String reference) {
		if (instruction.equalsIgnoreCase("WORD")) {
			return 3;
		} else if (instruction.equalsIgnoreCase("RESW")) {
			return 3 * parseInt(reference);
		} else if (instruction.equalsIgnoreCase("BYTE")) {
			if (reference.charAt(0) == 'X') {
				int length = (reference.length() - 3);
				return (length / 2) + (length % 2);
			} else if (reference.charAt(0) == 'C') {
				return reference.length() - 3;
			} else {
				errors.add("Invalid Byte Operand: " + reference);
				return 0;
			}
		} else if (instruction.equalsIgnoreCase("RESB")) {
			return parseInt(reference);
		} else if (directives.contains(instruction)) {
			return 0;
		} else {
			if (!instruction.isEmpty()) {
				int isFormatFour = 0;
				if (instruction.charAt(0) == '+') {
					isFormatFour = 1;
					instruction = instruction.substring(1, instruction.length());
				}

				if (opTable.containsKey(instruction)) {
					Operation operation = opTable.get(instruction);
					if (operation.getFormat() == 3) {
						return operation.getFormat() + isFormatFour;
					} else {
						return operation.getFormat();
					}

				} else {
					errors.add("Invalid Operation: " + instruction);
					return 0;
				}
			}
		}
		return 0;
	}

	private void updateLocationCounter(int incrementValue) {
		int locationCounterIntegerValue = parseInt(locationCounter, 16);
		locationCounterIntegerValue = locationCounterIntegerValue + incrementValue;
		locationCounter = addLeftZeros(Integer.toHexString(locationCounterIntegerValue), 4);
	}

	private void calculateProgramLength() {
		int locationCounterIntegerValue = parseInt(locationCounter, 16);
		int startingAddressIntegerValue = parseInt(startingAddress, 16);
		int programLengthIntegerValue = locationCounterIntegerValue - startingAddressIntegerValue;
		programLength = addLeftZeros(Integer.toHexString(programLengthIntegerValue), 6);
	}

	private String CalculateIOC(SicLine line, String instructionKey) {
		Operation operation = opTable.get(instructionKey);
		int format = operation.getFormat();
		String objectCode = "";
		if (format == 1) {
			objectCode = calculateFormat1(operation);
		} else if (format == 2) {
			objectCode = calculateFormat2(line, operation);
		} else if (format == 3) {
			if (line.getInstruction().charAt(0) == '+') {
				objectCode = calculateFormat4(line, operation);
			} else {
				objectCode = calculateFormat3(line, operation);
			}
		}
		return objectCode;
	}

	private String calculateFormat1(Operation operation) {
		return operation.getOpcode();
	}

	private String calculateFormat2(SicLine line, Operation operation) {
		String objectCode = operation.getOpcode();
		if (!line.getReference().isEmpty()) {
			String[] registerNames = line.getReference().split(",");
			String register1 = registers.get(registerNames[0]);
			if (register1 != null) {
				objectCode = objectCode + register1;
				if (registerNames.length == 2) {
					String register2 = registers.get(registerNames[1]);
					if (register2 != null) {
						objectCode = objectCode + register2;
					} else {
						errors.add("Invalid Register: " + registerNames[1]);
						return "";
					}
				} else {
					objectCode = objectCode + "0";
				}
			} else {
				errors.add("Invalid Register: " + registerNames[0]);
				return "";
			}

		} else {
			errors.add("Missing Register: Reference not found");
			return "";
		}
		return objectCode;
	}

	private String calculateFormat3(SicLine line, Operation operation) {
		String objectCode = operation.getOpcode();
		String binaryCode = hexToSixBin(objectCode);
		// 111000 n i x b p e (000000000000)
		boolean immediateFlag = false;
		String hexDisp = "000";
		int targetAddress = 0;
		int programCounter = parseInt(line.getLocation(), 16) + 3;
		int disp = 0;
		String reference = "";
		String lineReference = line.getReference();

		if (line.getInstruction().equalsIgnoreCase("RSUB")) {
			binaryCode = binaryCode + "110000";
		} else {
			// Getting n i x flags and reference
			if (lineReference.charAt(0) == '@') { // indirect addressing
				reference = lineReference.substring(1, lineReference.length());
				binaryCode = binaryCode + "100";
			} else if (lineReference.charAt(0) == '#') { // immediate addressing
				reference = lineReference.substring(1, lineReference.length());
				binaryCode = binaryCode + "010";
				immediateFlag = true;
			} else {
				binaryCode = binaryCode + "11";
				// indexed addressing
				if (lineReference.endsWith(",X")) {
					reference = lineReference.substring(0, lineReference.length() - 2);
					binaryCode = binaryCode + "1";
				} else {
					reference = lineReference;
					binaryCode = binaryCode + "0";
				}
			}
			// Get target address from reference
			if (reference.charAt(0) >= '0' && reference.charAt(0) <= '9') {
				targetAddress = parseInt(reference);
				if (immediateFlag) {
					binaryCode = binaryCode + "000";
					disp = targetAddress;
					hexDisp = addLeftZeros(Integer.toHexString(disp), 3);
					objectCode = binToHex(binaryCode, 3) + hexDisp;
					return objectCode;
				}
			} else { // label
				if (!reference.isEmpty()) {// ERRORS check missing operand if empty ?
					if (symbolTable.containsKey(reference)) {
						targetAddress = parseInt(symbolTable.get(reference), 16);
					} else {
						errors.add("Undefined Symbol: " + line.getReference());
					}
				}
			}
			disp = targetAddress - programCounter;

			if (-2048 <= disp && disp < 0) { // pc relative negative hex
				binaryCode = binaryCode + "010";
				hexDisp = Integer.toHexString(disp).substring(5);
			} else if (disp >= 0 && disp <= 2047) { // pc relative positive hex
				binaryCode = binaryCode + "010";
				hexDisp = addLeftZeros(Integer.toHexString(disp), 3);
			} else { // base relative
				if (baseFlag) {
					disp = targetAddress - baseValue;
					if (0 <= disp && disp < 4096) {
						binaryCode = binaryCode + "100";
						hexDisp = addLeftZeros(Integer.toHexString(disp), 3);
					} else {
						errors.add("Base out of bound displacement: " + disp + " " + baseValue);
					}
				} else {
					errors.add("Base not allowed and pc out of bound: " + disp + " " + programCounter);
				}
			}

		}
		objectCode = binToHex(binaryCode, 3) + hexDisp;
		return objectCode;
	}

	private String calculateFormat4(SicLine line, Operation operation) {
		String objectCode = operation.getOpcode();
		String binaryCode = hexToSixBin(objectCode);
		// 111000 n i x b p e (00000000000000000000)
		String hexAddress = "00000";
		String reference = "";
		String lineReference = line.getReference();

		if (line.getInstruction().equalsIgnoreCase("RSUB")) {
			binaryCode = binaryCode + "110001";
		} else {
			// Getting n i x flags and reference
			if (lineReference.charAt(0) == '@') { // indirect addressing
				reference = lineReference.substring(1, lineReference.length());
				binaryCode = binaryCode + "100";
			} else if (lineReference.charAt(0) == '#') { // immediate addressing
				reference = lineReference.substring(1, lineReference.length());
				binaryCode = binaryCode + "010";
			} else {
				binaryCode = binaryCode + "11";
				// indexed addressing
				if (lineReference.endsWith(",X")) {
					reference = lineReference.substring(0, lineReference.length() - 2);
					binaryCode = binaryCode + "1";
				} else {
					reference = lineReference;
					binaryCode = binaryCode + "0";
				}
			}
			binaryCode = binaryCode + "001";
			// Get target address from reference
			if (reference.charAt(0) >= '0' && reference.charAt(0) <= '9') {
				hexAddress = addLeftZeros(Integer.toHexString(parseInt(reference)), 5);
			} else { // label
				if (!reference.isEmpty()) {// ERRORS check missing operand if empty ?
					if (symbolTable.containsKey(reference)) {
						hexAddress = addLeftZeros(symbolTable.get(reference), 5);
					} else {
						errors.add("Undefined Symbol: " + line.getReference());
					}
				}
			}
		}
		objectCode = binToHex(binaryCode, 3) + hexAddress;
		return objectCode;
	}

	private void pass1(final String sourceFileName) throws IOException {
		File copyFile = new File("copy.txt");
		FileWriter fw = new FileWriter(copyFile);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write("Loc\tLabel\tInstruction\tOperand");
		bw.newLine();
		Scanner s = new Scanner(new File(sourceFileName));
		int incrementValue = 0;
		String instruction = "";
		boolean firstStart = true;
		while (s.hasNext() && !instruction.equalsIgnoreCase("END")) {
			String inputLine = s.nextLine();
			SicLine sicLine = new SicLine();
			if (inputLine.charAt(0) != '.') {
				String[] text = inputLine.split("\t");
				String label = "";
				String reference = "";
				if (text.length == 1) {
					instruction = text[0].strip();
				} else if (text.length == 2) {
					if (text[1].equalsIgnoreCase("RSUB")) {
						label = text[0].strip();
						instruction = text[1].strip();
					} else {
						instruction = text[0].strip();
						reference = text[1].strip();
					}
				} else {
					label = text[0].strip();
					instruction = text[1].strip();
					reference = text[2].strip();
				}

				if (instruction.equalsIgnoreCase("START")) {
					if (firstStart) {
						firstStart = false;
						locationCounter = reference;
						startingAddress = addLeftZeros(locationCounter, 6);
					} else {
						errors.add("Multiple START: " + instruction);
					}
				}

				updateLocationCounter(incrementValue);

				if (label != null && !label.isEmpty()) {
					if (symbolTable.containsKey(label)) {
						errors.add("Duplicate Label: " + label);
					} else {
						symbolTable.put(label, locationCounter);
					}
				}

				incrementValue = calculateIncrementValue(instruction, reference);

				sicLine.setLocation(locationCounter);
				sicLine.setLabel(label);
				sicLine.setInstruction(instruction);
				sicLine.setReference(reference);
			} else {
				sicLine.setLine(inputLine);
				sicLine.setCommentLine(true);
			}
			bw.write(sicLine.toString());
			bw.newLine();
			instructionLines.add(sicLine);
		}
		calculateProgramLength();
		System.out.println("Staring Address: " + startingAddress);
		System.out.println("Program Length: " + programLength);
		bw.close();
		instructionLines.forEach(System.out::println);
		errors.forEach(System.out::println);
	}

	private void pass2() throws IOException {
		File HTEFile = new File("HTE.txt");
		FileWriter fwHTE = new FileWriter(HTEFile);
		BufferedWriter bwHTE = new BufferedWriter(fwHTE);
		String HTEConsole = "";
		File outputFile = new File("pass2.txt");
		FileWriter fwOutputFile = new FileWriter(outputFile);
		BufferedWriter bwOutputFile = new BufferedWriter(fwOutputFile);

		System.out.println("Loc\tLabel\tInstruction\tOperand\t	Object Code");
		bwOutputFile.write("Loc\tLabel\tInstruction\tOperand\t	Object Code");
		bwOutputFile.newLine();

		HTEConsole = HTEConsole + getHeaderFileContent() + "\n";
		bwHTE.write(getHeaderFileContent());
		bwHTE.newLine();

		String textObjectCode = "";
		String textStartingAddress = "";
		String textLength = "";

		for (SicLine line : instructionLines) {
			if (!line.isCommentLine()) {
				String referenceAddress = "";
				String objectCode = "";
				line.setObjectCode(objectCode);
				String instructionKey = line.getInstruction();
				if (instructionKey.charAt(0) == '+') {
					instructionKey = instructionKey.substring(1, instructionKey.length());
				}
				if (opTable.containsKey(instructionKey)) {
					objectCode = CalculateIOC(line, instructionKey);
					line.setObjectCode(objectCode);
				} else if (directives.contains(line.getInstruction())) {

					if (line.getInstruction().equalsIgnoreCase("BYTE")) {
						if (line.getReference().charAt(0) == 'X') {
							objectCode = line.getReference().substring(2, line.getReference().length() - 1);
						} else if (line.getReference().charAt(0) == 'C') {
							String characters = line.getReference().substring(2, line.getReference().length() - 1);
							for (int i = 0; i < characters.length(); i++) {
								int asciiIntegerValue = characters.charAt(i);
								String asciiHexValue = addLeftZeros(Integer.toHexString(asciiIntegerValue), 2);
								objectCode = objectCode.concat(asciiHexValue);
							}
						}
						line.setObjectCode(objectCode);
					} else if (line.getInstruction().equalsIgnoreCase("WORD")) {
						objectCode = addLeftZeros(Integer.toHexString(parseInt(line.getReference())), 6);
						line.setObjectCode(objectCode);
					} else if (line.getInstruction().equalsIgnoreCase("BASE")) {
						baseFlag = true;
						if (line.getReference().charAt(0) >= '0' && line.getReference().charAt(0) <= '9') {
							baseValue = parseInt(line.getReference());
						} else if (line.getReference() != null && !line.getReference().isEmpty()) {
							if (symbolTable.containsKey(line.getReference())) {
								baseValue = parseInt(symbolTable.get(line.getReference()), 16);
							} else {
								errors.add("Undefined Symbol: " + line.getReference());
							}
						} else {
							errors.add("Invalid operand for base: " + line.getReference());
						}

					}
				} else {
					errors.add("Invalid Operation: " + line.getInstruction());
				}
				if (textObjectCode.isEmpty() && !line.getObjectCode().isEmpty()) {
					// start new text record
					textStartingAddress = addLeftZeros(line.getLocation(), 6);
					textObjectCode = line.getObjectCode();
				} else if (!line.getObjectCode().isEmpty()) {
					// if line has object code
					// and text record has buffered object code
					if (textObjectCode.length() + line.getObjectCode().length() <= 60) {
						textObjectCode = textObjectCode.concat(line.getObjectCode());
					} else {
						int length = textObjectCode.length();
						int bytesLength = (length / 2) + (length % 2);
						textLength = addLeftZeros(Integer.toHexString(bytesLength), 2);
						HTEConsole = HTEConsole + getTextFileContent(textStartingAddress, textLength, textObjectCode)
								+ "\n";
						bwHTE.write(getTextFileContent(textStartingAddress, textLength, textObjectCode));
						bwHTE.newLine();
						textStartingAddress = addLeftZeros(line.getLocation(), 6);
						textObjectCode = line.getObjectCode();
					}
				} else if (!textObjectCode.isEmpty()) {
					// if line doesn't have object code
					// and text record has buffered object code
					int length = textObjectCode.length();
					int bytesLength = (length / 2) + (length % 2);
					textLength = addLeftZeros(Integer.toHexString(bytesLength), 2);
					HTEConsole = HTEConsole + getTextFileContent(textStartingAddress, textLength, textObjectCode)
							+ "\n";
					bwHTE.write(getTextFileContent(textStartingAddress, textLength, textObjectCode));
					bwHTE.newLine();
					textObjectCode = "";
				}
			}
			System.out.println(line);
			bwOutputFile.write(line.toString());
			bwOutputFile.newLine();
		}

		errors.forEach(System.out::println);
		HTEConsole = HTEConsole + getEndFileContent();
		bwHTE.write(getEndFileContent());
		bwHTE.newLine();
		System.out.println("------------------");
		System.out.println("HTE Record:");
		System.out.println(HTEConsole);
		bwHTE.close();
		bwOutputFile.close();
	}

	private String getHeaderFileContent() {
		int programNameIndex = 0;
		while (instructionLines.get(programNameIndex).isCommentLine()) {
			programNameIndex++;
		}
		String programName = instructionLines.get(programNameIndex).getLabel();
		return "H" + String.format("%6.6s", programName) + startingAddress + programLength;
	}

	private String getTextFileContent(String textStartingAddress, String textLength, String textObjectCode) {
		return "T" + textStartingAddress + textLength + textObjectCode;
	}

	private String getEndFileContent() {
		return "E" + startingAddress;
	}

	public static void main(String[] args) throws IOException {

		Scanner s = new Scanner(System.in);
		System.out.println("Enter Operations file name");
		String operationsFile = s.next() + ".txt";
		System.out.println("Enter Source file name");
		String sourceFile = s.next() + ".txt";

		SicXe sicXe = new SicXe(operationsFile);
		sicXe.printOpTable();
		sicXe.pass1(sourceFile);
		sicXe.printSymbolTable();
		sicXe.pass2();
	}

}
