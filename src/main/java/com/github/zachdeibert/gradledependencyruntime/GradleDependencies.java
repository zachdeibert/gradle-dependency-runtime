package com.github.zachdeibert.gradledependencyruntime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressListener;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.logging.sink.OutputEventRenderer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.time.TrueTimeProvider;

import com.github.zachdeibert.mavendependencyruntime.ClassPathScanner;
import com.github.zachdeibert.mavendependencyruntime.Dependency;
import com.github.zachdeibert.mavendependencyruntime.DependencyScope;
import com.github.zachdeibert.mavendependencyruntime.MavenDependencies;
import com.github.zachdeibert.mavendependencyruntime.Repository;

/**
 * The class that contains all of the methods needed for downloading and
 * injecting dependencies into the classpath.
 * 
 * @author Zach Deibert
 * @since 1.0.0
 */
public abstract class GradleDependencies {
	/**
	 * The scopes to download dependencies for by default
	 * 
	 * @since 1.0.0
	 */
	private static final DependencyScope[] DEFAULT_SCOPES = { DependencyScope.COMPILE, DependencyScope.RUNTIME };

	/**
	 * Downloads all of the dependencies specified in the build script
	 * 
	 * @param buildGradle
	 *            The stream containing the build script file
	 * @param scopes
	 *            The scopes to download for
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(InputStream buildGradle, DependencyScope... scopes) throws IOException {
		ListenerManager listenerManager = new DefaultListenerManager();
		OutputEventListener listener = new OutputEventRenderer();
		ProgressListener progressListener = new ProgressLoggingBridge(listener);
		TimeProvider timeProvider = new TrueTimeProvider();
		ProgressLoggerFactory progressLoggerFactory = new DefaultProgressLoggerFactory(progressListener, timeProvider);
		ServiceRegistry serviceRegistry = new DefaultServiceRegistry();
		GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry = new DefaultGradleUserHomeScopeServiceRegistry(
				serviceRegistry, new Object());
		DefaultGradleLauncherFactory factory = new DefaultGradleLauncherFactory(listenerManager, progressLoggerFactory,
				userHomeDirServiceRegistry);
		StartParameter startParameter = new StartParameter();
		BuildRequestMetaData metaData = new DefaultBuildRequestMetaData(System.currentTimeMillis());
		BuildCancellationToken token = new DefaultBuildCancellationToken();
		BuildEventConsumer buildEventConsumer = new NoOpBuildEventConsumer();
		BuildRequestContext requestContext = new DefaultBuildRequestContext(metaData, token, buildEventConsumer);
		GradleLauncher gradleLauncher = factory.newInstance(startParameter, requestContext, serviceRegistry);
		final Set<Dependency> downloaded = new HashSet<Dependency>();
		gradleLauncher.getGradle().afterProject(new Action<Project>() {
			public void execute(Project t) {
				System.out.println("Project callback called.");
				try {
					RepositoryHandler repositoryHandler = t.getRepositories();
					List<Repository> repositories = new ArrayList<Repository>();
					for (ArtifactRepository repository : repositoryHandler) {
						if (repository instanceof MavenArtifactRepository) {
							MavenArtifactRepository mavenRepository = (MavenArtifactRepository) repository;
							Repository repo = new Repository(mavenRepository.getUrl().toURL().toString());
							repositories.add(repo);
						}
					}
					ConfigurationContainer configurations = t.getConfigurations();
					Configuration configuration = configurations.getByName("runtime");
					DependencySet dependencies = configuration.getAllDependencies();
					List<Dependency> deps = new ArrayList<Dependency>();
					for (org.gradle.api.artifacts.Dependency dependency : dependencies) {
						Dependency dep = new Dependency(dependency.getGroup(), dependency.getName(),
								dependency.getVersion(), DependencyScope.RUNTIME);
						deps.add(dep);
					}
					downloaded.addAll(MavenDependencies.download(repositories, deps));
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		File temp = Files.createTempDirectory("gradle-dependency-runtime-").toFile();
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(new File(temp, "build.gradle"));
			IOUtils.copy(buildGradle, stream);
			stream.close();
			stream = null;
			try {
				System.out.println("Loading project...");
				gradleLauncher.getSettings().project(temp);
				System.out.println("Project loaded.");
			} catch (RuntimeException ex) {
				throw new IOException(ex);
			}
		} finally {
			IOException ex = null;
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					ex = e;
				}
			}
			if (temp.exists()) {
				FileUtils.deleteDirectory(temp);
			}
			if (ex != null) {
				throw ex;
			}
		}
		return downloaded;
	}

	/**
	 * Downloads all of the dependencies specified in the build script for the
	 * default scopes
	 * 
	 * @param buildGradle
	 *            The stream containing the build script file
	 * @return The set of all dependencies that were downloaded
	 * @see GradleDependencies#DEFAULT_SCOPES
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(InputStream buildGradle) throws IOException {
		return download(buildGradle, DEFAULT_SCOPES);
	}

	/**
	 * Downloads all of the dependencies specified in the build script
	 * 
	 * @param gradlePath
	 *            The url to the build script
	 * @param scopes
	 *            The scopes to download for
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(URL gradlePath, DependencyScope... scopes) throws IOException {
		InputStream gradle = gradlePath.openStream();
		Set<Dependency> downloaded = download(gradle, scopes);
		gradle.close();
		return downloaded;
	}

	/**
	 * Downloads all of the dependencies specified in the build script for the
	 * default scopes
	 * 
	 * @param gradlePath
	 *            The url to the build script
	 * @return The set of all dependencies that were downloaded
	 * @see GradleDependencies#DEFAULT_SCOPES
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(URL gradlePath) throws IOException {
		return download(gradlePath, DEFAULT_SCOPES);
	}

	/**
	 * Downloads all of the dependencies specified in the build script
	 * 
	 * @param gradlePath
	 *            The path to the build script in the classpath
	 * @param scopes
	 *            The scopes to download for
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(String gradlePath, DependencyScope... scopes) throws IOException {
		return download(ClassLoader.getSystemResource(gradlePath), scopes);
	}

	/**
	 * Downloads all of the dependencies specified in the build script for the
	 * default scopes
	 * 
	 * @param gradlePath
	 *            The path to the build script in the classpath
	 * @return The set of all dependencies that were downloaded
	 * @see GradleDependencies#DEFAULT_SCOPES
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(String gradlePath) throws IOException {
		return download(gradlePath, DEFAULT_SCOPES);
	}

	/**
	 * Downloads all of the dependencies specified in the build script
	 * 
	 * @param groupId
	 *            The group ID of the artifact to download dependencies for
	 * @param artifactId
	 *            The artifact ID to download dependencies for
	 * @param scopes
	 *            The scopes to download for
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(String groupId, String artifactId, DependencyScope... scopes)
			throws IOException {
		return download(String.format("META-INF/gradle/%s/%s/build.gradle", groupId, artifactId), scopes);
	}

	/**
	 * Downloads all of the dependencies specified in the build script for the
	 * default scopes
	 * 
	 * @param groupId
	 *            The group ID of the artifact to download dependencies for
	 * @param artifactId
	 *            The artifact ID to download dependencies for
	 * @return The set of all dependencies that were downloaded
	 * @see GradleDependencies#DEFAULT_SCOPES
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(String groupId, String artifactId) throws IOException {
		return download(groupId, artifactId, DEFAULT_SCOPES);
	}

	/**
	 * Downloads all of the dependencies specified in all build scripts
	 * contained inside the manifest in the current classpath
	 * 
	 * @param scopes
	 *            The scopes to download for
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download(DependencyScope... scopes) throws IOException {
		Set<Dependency> deps = new HashSet<Dependency>();
		for (String group : ClassPathScanner.listSystemResources("META-INF/gradle")) {
			for (String artifact : ClassPathScanner.listSystemResources("META-INF/gradle/".concat(group))) {
				deps.addAll(download(group, artifact, scopes));
			}
		}
		return deps;
	}

	/**
	 * Downloads all of the dependencies specified in all build scripts
	 * contained inside the manifest in the current classpath for the default
	 * scopes
	 * 
	 * @return The set of all dependencies that were downloaded
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	public static Set<Dependency> download() throws IOException {
		return download(DEFAULT_SCOPES);
	}
}
