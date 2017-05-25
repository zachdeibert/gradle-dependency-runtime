package com.github.zachdeibert.gradledependencyruntime;

/**
 * The main class that should never be called, but needs to exist so the maven
 * runtime can be packages with the gradle runtime in the library jar.
 * 
 * @author Zach Deibert
 * @since 1.0.0
 */
class Main {
	/**
	 * Prints an error message
	 * 
	 * @param args
	 *            The command line parameters to this application
	 * @since 1.0.0
	 */
	public static void main(String[] args) {
		System.err.println("You cannot run this jar as an application");
	}
}
