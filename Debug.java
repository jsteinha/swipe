class Debug {
	public static void p(Object obj, String name) {
		System.out.println("\033[91m"+name+" = "+obj+"\033[0m");
	}
	public static void p(Object obj) {
		System.out.println("\033[91m"+obj+"\033[0m");
	}
	public static void main(String[] args) {
		Debug.p(5);
	}
}
