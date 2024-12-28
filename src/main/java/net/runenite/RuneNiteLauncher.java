package net.runenite;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.Launcher;
import static net.runelite.launcher.Launcher.REPO_DIR;
import static net.runelite.launcher.Launcher.RUNELITE_DIR;
import net.runelite.launcher.LauncherSettings;
import net.runelite.launcher.SplashScreen;
import net.runelite.launcher.VerificationException;
import net.runelite.launcher.beans.Artifact;
import net.runelite.launcher.beans.Bootstrap;
import net.runenite.utils.ResourceManager;

@Slf4j
public class RuneNiteLauncher
{
	public static final File RUNENITE_DIR = new File(RUNELITE_DIR, "runenite");
	private static boolean patchAnyway = false;
	private static boolean ignoreMissingArtifacts = false;

	private static final List<Artifact> verifiedPatchableArtifacts = new ArrayList<>();
	private static final List<Artifact> artifactsToPatch = new ArrayList<>();

	public static void extendOptionsParser(OptionParser parser)
	{
		parser.accepts("patch-anyway", "Whether or not to blindly apply any existing patches.");
		parser.accepts("ignore-missing-artifacts", "Continue with patching even when some artifacts are missing.");
	}

	public static boolean parseOptions(OptionSet options)
	{
		patchAnyway = options.has("patch-anyway");
		ignoreMissingArtifacts = options.has("ignore-missing-artifacts");

		return true;
	}

	public static Bootstrap getBootstrap() throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, VerificationException
	{
		ensureDirectoryExists(RUNENITE_DIR);

		File bootstrapFile = new File(RUNENITE_DIR, "bootstrap.json");
		if (!bootstrapFile.exists())
		{
			ResourceManager.copyResource("bootstrap.json", bootstrapFile);
		}

		return parseBootstrap(Files.readAllBytes(bootstrapFile.toPath()));
	}

	protected static Bootstrap getBundledBootstrap() throws IOException
	{
		try (InputStream stream = RuneNiteLauncher.class.getResourceAsStream("bootstrap.json"))
		{
			assert stream != null;

			return parseBootstrap(stream.readAllBytes());
		}
	}

	private static Bootstrap parseBootstrap(byte[] definition)
	{
		Gson g = new Gson();
		return g.fromJson(new InputStreamReader(new ByteArrayInputStream(definition)), Bootstrap.class);
	}

	public static void updateLauncher(Bootstrap ignoredBootstrap, LauncherSettings ignoredSettings, String[] ignoredArgs)
	{
		//
	}

	public static void download(List<Artifact> artifacts, boolean ignoredNodiff) throws IOException, VerificationException
	{
		SplashScreen.stage(.15, "Downloading", "Unpacking bundled artifacts");
		Bootstrap bundledBootstrap = getBundledBootstrap();

		for (Artifact artifact : artifacts)
		{
			if (Arrays.stream(bundledBootstrap.getArtifacts()).anyMatch(a -> a.getName().equals(artifact.getName()) && a.getHash().equals(artifact.getHash())))
			{
				verifiedPatchableArtifacts.add(artifact);
			}
		}

		int total = artifacts.size();
		int completed = 0;

		List<String> missingArtifacts = new ArrayList<>();

		for (Artifact artifact : artifacts)
		{
			completed++;
			SplashScreen.stage(.15, .80, null, artifact.getName(), completed, total, false);
			File dest = new File(REPO_DIR, artifact.getName());

			String hash;
			try
			{
				hash = Launcher.hash(dest);
			}
			catch (FileNotFoundException ex)
			{
				hash = null;
			}
			catch (IOException ex)
			{
				// noinspection ResultOfMethodCallIgnored
				dest.delete();
				hash = null;
			}

			if (Objects.equals(hash, artifact.getHash()))
			{
				artifactsToPatch.add(artifact);
				continue;
			}

			if (verifiedPatchableArtifacts.contains(artifact) && hash == null)
			{
				log.info("Unpacking known artifact from bundled resources {}", artifact.getName());
				ResourceManager.copyResource("artifacts/" + artifact.getName(), dest);
				artifactsToPatch.add(artifact);
				continue;
			}

			if (verifiedPatchableArtifacts.contains(artifact) && !patchAnyway)
			{
				log.info("Hash for {} is bad or unknown, likely already patched. Skipping...", artifact.getName());
				continue;
			}

			if (patchAnyway)
			{
				log.info("Hash for {} is bad or unknown, but patching anyway.", artifact.getName());
				artifactsToPatch.add(artifact);
				continue;
			}

			missingArtifacts.add(artifact.getName());
		}

		if (!missingArtifacts.isEmpty() && !ignoreMissingArtifacts)
		{
			throw new IOException("Missing or bad artifacts: " + String.join(", ", missingArtifacts));
		}
	}

	public static void verifyJarHashes(List<Artifact> ignoredArtifacts) throws VerificationException, IOException
	{
		// We currently don't verify the hashes of the jars.
		// We could do this against a custom .patched file, but it's not worth the effort.
		// Instead, we'll just use this step to patch our artifacts.

		if (artifactsToPatch.isEmpty())
		{
			return;
		}

		ArtifactPatcher.unpackBundledPatchResources();
		for (Artifact artifact : artifactsToPatch)
		{
			ArtifactPatcher.patch(artifact);
		}
	}

	public static void ensureDirectoryExists(File directory)
	{
		// noinspection ResultOfMethodCallIgnored
		directory.mkdirs();
	}
}
