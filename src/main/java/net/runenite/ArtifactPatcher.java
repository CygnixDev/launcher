package net.runenite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.security.jarsigner.JarSigner;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import static net.runelite.launcher.Launcher.REPO_DIR;
import net.runelite.launcher.beans.Artifact;
import static net.runenite.RuneNiteLauncher.RUNENITE_DIR;
import static net.runenite.RuneNiteLauncher.ensureDirectoryExists;
import net.runenite.patches.PatchGameClientLocalhostCheck;
import net.runenite.patches.PatchGameClientPort;
import net.runenite.patches.PatchGameClientRsaSignature;
import net.runenite.patches.PatchRuneLiteApi;
import net.runenite.patches.PatchRuneLiteClient;
import net.runenite.utils.ResourceManager;

@Slf4j
public class ArtifactPatcher
{
	public static final File RESOURCES_DIR = new File(RUNENITE_DIR, "resources");
	public static final File TEMPORARY_DIR = new File(RUNENITE_DIR, "temp");

	private static final List<Patch> patches = List.of(
		new PatchGameClientRsaSignature(),
		new PatchGameClientLocalhostCheck(),
		new PatchGameClientPort(),
		new PatchRuneLiteClient(),
		new PatchRuneLiteApi()
	);

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void patch(Artifact artifact)
	{
		String artifactName = getArtifactName(artifact);
		if (artifactName == null)
		{
			return;
		}

		var patchFiles = patches.stream()
			.filter(p -> p.appliesTo(artifactName))
			.collect(Collectors.toList());

		if (patchFiles.isEmpty())
		{
			return;
		}

		ensureDirectoryExists(TEMPORARY_DIR);

		File tempWorkingDir = new File(TEMPORARY_DIR, artifactName);
		deleteDir(tempWorkingDir);
		tempWorkingDir.mkdirs();

		File artifactFile = new File(REPO_DIR, artifact.getName());
		try (ZipFile inputFile = new ZipFile(artifactFile))
		{
			log.info("Extracting {} to {}", artifactFile, tempWorkingDir);
			inputFile.extractAll(tempWorkingDir.getAbsolutePath());

			boolean previouslySigned = wasSigned(tempWorkingDir);

			for (Patch patch : patchFiles)
			{
				patch.apply(artifact, tempWorkingDir);
			}

			if (previouslySigned)
			{
				File metaInfDir = tempWorkingDir.toPath().resolve("META-INF").toFile();
				deleteDir(metaInfDir);
			}

			String timestamp = Long.toString(System.currentTimeMillis());
			File patchedJar = new File(TEMPORARY_DIR, artifactName + "-" + timestamp + "-patched.jar");
			log.info("Compressing patched artifact to {}", patchedJar);

			try (ZipFile outputFile = new ZipFile(patchedJar))
			{
				try (Stream<Path> paths = Files.walk(tempWorkingDir.toPath(), 1))
				{
					for (Path path : paths.collect(Collectors.toList()))
					{
						File file = path.toFile();
						if (file.equals(tempWorkingDir))
						{
							continue;
						}

						if (file.isFile())
						{
							outputFile.addFile(file);
						}
						else
						{
							outputFile.addFolder(file);
						}
					}
				}

				outputFile.setCharset(inputFile.getCharset());
			}

			if (previouslySigned)
			{
				log.info("Re-signing patched artifact");
				sign(patchedJar.toPath());
			}

			log.info("Moving patched artifact to {}", artifactFile);

			Files.move(patchedJar.toPath(), artifactFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e)
		{
			log.error("Error patching artifact", e);
		}
		finally
		{
			log.info("Deleting temporary working dir.");
			deleteDir(TEMPORARY_DIR);
		}
	}

	private static boolean wasSigned(File tempWorkingDir)
	{
		File metaInfDir = tempWorkingDir.toPath().resolve("META-INF").toFile();
		if (!metaInfDir.exists())
		{
			return false;
		}

		File[] signatureFiles = metaInfDir.listFiles((dir, name) ->
			name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")
		);

		return signatureFiles != null && signatureFiles.length > 0;
	}

	public static void unpackBundledPatchResources() throws IOException
	{
		ensureDirectoryExists(RESOURCES_DIR);

		List<String> patches = ResourceManager.getResourcesAtPath("resources");
		if (patches.isEmpty())
		{
			return;
		}

		for (String patch : patches)
		{
			File dest = new File(RESOURCES_DIR, patch);
			ensureDirectoryExists(dest.getParentFile());

			if (!dest.exists())
			{
				log.info("Unpacking bundled patch resource {}", patch);
				ResourceManager.copyResource("resources/" + patch, dest);
			}
		}
	}

	private static String getArtifactName(Artifact artifact)
	{
		String name = artifact.getName();
		if (name.endsWith(".jar"))
		{
			return name.substring(0, name.length() - 4);
		}

		return null;
	}

	private static void deleteDir(File file)
	{
		File[] contents = file.listFiles();
		if (contents != null)
		{
			for (File f : contents)
			{
				if (!Files.isSymbolicLink(f.toPath()))
				{
					deleteDir(f);
				}
			}
		}

		// noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	private static void sign(Path path)
		throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, UnrecoverableEntryException
	{
		File fakeCertificate = new File(RESOURCES_DIR, "fake-cert.jks");
		if (!fakeCertificate.exists())
		{
			log.error("Unable to sign jar, fake certificate not found");
			return;
		}

		char[] password = "123456".toCharArray();
		KeyStore store = KeyStore.getInstance(fakeCertificate, password);
		KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry("test", new KeyStore.PasswordProtection(password));

		JarSigner signer = new JarSigner.Builder(entry).build();

		Path output = path.getParent().resolve(path.getFileName() + ".signed");
		try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(path.toFile());
			FileOutputStream outputStream = new FileOutputStream(output.toFile()))
		{
			signer.sign(zipFile, outputStream);
		}

		Files.move(output, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}
}
