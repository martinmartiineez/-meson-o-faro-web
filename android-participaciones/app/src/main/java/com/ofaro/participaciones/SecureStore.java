package com.ofaro.participaciones;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Almacena secretos locales cifrados con una clave no exportable de Android Keystore. */
final class SecureStore {
    private static final String ANDROID_KEYSTORE="AndroidKeyStore";
    private static final String ALIAS="ofaro_management_key_v1";
    private static final String PREF_CIPHER="secure_management_key_v1";
    private static final String PREFIX="gcm1:";

    private SecureStore(){}

    static String readManagementKey(Context context,SharedPreferences prefs){
        if(context==null||prefs==null)return"";
        /* Compatibilidad: cualquier pantalla antigua que todavía escriba prefs[\"key\"]
           queda migrada al siguiente acceso. */
        String legacy=prefs.getString("key","");
        if(legacy!=null&&!legacy.trim().isEmpty()){
            String clean=legacy.trim();
            if(writeManagementKey(context,prefs,clean))prefs.edit().remove("key").apply();
            return clean;
        }
        String stored=prefs.getString(PREF_CIPHER,"");
        if(stored==null||stored.trim().isEmpty())return"";
        try{return decrypt(stored.trim());}catch(Exception e){return"";}
    }

    static boolean writeManagementKey(Context context,SharedPreferences prefs,String value){
        if(context==null||prefs==null)return false;
        String clean=value==null?"":value.trim();
        if(clean.isEmpty()){
            prefs.edit().remove(PREF_CIPHER).remove("key").apply();
            return true;
        }
        try{
            String encrypted=encrypt(clean);
            prefs.edit().putString(PREF_CIPHER,encrypted).remove("key").apply();
            return true;
        }catch(Exception e){
            /* Nunca degradamos silenciosamente a texto plano. */
            return false;
        }
    }

    static boolean hasEncryptedKey(SharedPreferences prefs){return prefs!=null&&prefs.contains(PREF_CIPHER);}

    private static String encrypt(String plain)throws Exception{
        SecretKey key=getOrCreateKey();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] iv=cipher.getIV();
        byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return PREFIX+Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
    }

    private static String decrypt(String payload)throws Exception{
        if(!payload.startsWith(PREFIX))throw new Exception("Formato de secreto no compatible.");
        String body=payload.substring(PREFIX.length());
        int sep=body.indexOf(':');if(sep<=0)throw new Exception("Secreto cifrado no válido.");
        byte[] iv=Base64.decode(body.substring(0,sep),Base64.NO_WRAP);
        byte[] encrypted=Base64.decode(body.substring(sep+1),Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8).trim();
    }

    private static SecretKey getOrCreateKey()throws Exception{
        KeyStore ks=KeyStore.getInstance(ANDROID_KEYSTORE);ks.load(null);
        java.security.Key existing=ks.getKey(ALIAS,null);
        if(existing instanceof SecretKey)return(SecretKey)existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
