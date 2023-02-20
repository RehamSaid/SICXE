package pkg;
//Reham Said 19103134
public class Util {

	public static String addLeftZeros(String number, int numberOfDigits) {
		while (number.length() < numberOfDigits) {
			number = "0" + number;
		}
		return number;
	}

	public static String hexToSixBin(String hex) {
		int intValue = Integer.parseInt(hex, 16);
		String binaryCode = Integer.toBinaryString(intValue);
		binaryCode = addLeftZeros(binaryCode, 8);
		return binaryCode.substring(0, 6);
	}
	
	public static String binToHex(String bin, int numberOfDigits) {
		int intValue = Integer.parseInt(bin, 2);
		String hex = Integer.toHexString(intValue);
		return addLeftZeros(hex, numberOfDigits);
	}

}
