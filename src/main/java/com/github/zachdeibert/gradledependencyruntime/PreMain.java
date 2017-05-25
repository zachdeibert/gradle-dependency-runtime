package com.github.zachdeibert.gradledependencyruntime;

/**
 * The main class that is called from the actual
 * {@link com.github.zachdeibert.mavendependencyruntime.Main} that downloads the
 * dependencies that are available in Gradle
 * 
 * @author Zach Deibert
 * @since 1.0.0
 */
class PreMain {
	/**
	 * Downloads all of the Gradle dependencies
	 * 
	 * @param args
	 *            The command line parameters to this application
	 * @since 1.0.0
	 * @throws Exception
	 *             If an error occurs
	 */
	public static void main(String[] args) throws Exception {
		GradleDependencies.download();
	}
}
