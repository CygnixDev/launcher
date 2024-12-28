package net.runenite.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

@Slf4j
public class PrivateKeyReader
{
	public static PrivateKey readPrivateKey(File file)
	{
		try (PEMParser pemParser = new PEMParser(new FileReader(file)))
		{
			Object object = pemParser.readObject();

			if (object instanceof PrivateKeyInfo)
			{
				JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

				return converter.getPrivateKey((PrivateKeyInfo) object);
			}

			throw new IllegalArgumentException("Not a valid private key file");
		}
		catch (IOException e)
		{
			log.error("Error reading private key", e);
		}

		return null;
	}

	public static RSAPrivateCrtKey readRsaPrivateCrtKey(File file)
	{
		PrivateKey key = readPrivateKey(file);

		if (key instanceof RSAPrivateCrtKey)
		{
			return (RSAPrivateCrtKey) key;
		}

		log.error("Private key is not an RSA Private CRT key");

		return null;
	}
}
