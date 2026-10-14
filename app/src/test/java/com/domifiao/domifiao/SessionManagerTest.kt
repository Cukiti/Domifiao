package com.domifiao.domifiao

import android.content.Context
import android.content.SharedPreferences
import com.domifiao.domifiao.data.SessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * Tests unitarios para SessionManager
 */
@RunWith(MockitoJUnitRunner::class)
class SessionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var sessionManager: SessionManager

    @Before
    fun setup() {
        // Configurar mocks
        `when`(mockContext.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }

        sessionManager = SessionManager(mockContext)
    }

    @Test
    fun testSaveSession_savesCorrectly() {
        // Preparar
        val role = "CLIENT"
        val id = "user123"
        val name = "Juan Pérez"
        val photo = "base64string"

        // Ejecutar
        sessionManager.saveSession(role, id, name, photo)

        // Verificar que se guardaron los datos correctos
        verify(mockEditor).putString("user_role", role)
        verify(mockEditor).putString("user_id", id)
        verify(mockEditor).putString("user_name", name)
        verify(mockEditor).putString("user_photo", photo)
        verify(mockEditor).putLong(eq("last_login"), anyLong())
        verify(mockEditor).apply()
    }

    @Test
    fun testIsLoggedIn_returnsTrueWhenUserIdExists() {
        // Preparar
        `when`(mockSharedPreferences.getString("user_id", null)).thenReturn("user123")

        // Verificar
        assertTrue(sessionManager.isLoggedIn())
    }

    @Test
    fun testIsLoggedIn_returnsFalseWhenUserIdIsNull() {
        // Preparar
        `when`(mockSharedPreferences.getString("user_id", null)).thenReturn(null)

        // Verificar
        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun testIsClient_returnsTrueForClientRole() {
        // Preparar
        `when`(mockSharedPreferences.getString("user_role", null)).thenReturn("CLIENT")

        // Verificar
        assertTrue(sessionManager.isClient())
    }

    @Test
    fun testIsColmado_returnsTrueForColmadoRole() {
        // Preparar
        `when`(mockSharedPreferences.getString("user_role", null)).thenReturn("COLMADO")

        // Verificar
        assertTrue(sessionManager.isColmado())
    }

    @Test
    fun testClearSession_clearsAllData() {
        // Ejecutar
        sessionManager.clearSession()

        // Verificar
        verify(mockEditor).clear()
        verify(mockEditor).apply()
    }

    @Test
    fun testUpdateLastActivity_updatesTimestamp() {
        // Ejecutar
        sessionManager.updateLastActivity()

        // Verificar
        verify(mockEditor).putLong(eq("last_activity"), anyLong())
        verify(mockEditor).apply()
    }

    @Test
    fun testSetBiometricEnabled_savesPreference() {
        // Ejecutar
        sessionManager.setBiometricEnabled(true)

        // Verificar
        verify(mockEditor).putBoolean("biometric_enabled", true)
        verify(mockEditor).apply()
    }

    @Test
    fun testIsBiometricEnabled_returnsCorrectValue() {
        // Preparar
        `when`(mockSharedPreferences.getBoolean("biometric_enabled", false)).thenReturn(true)

        // Verificar
        assertTrue(sessionManager.isBiometricEnabled())
    }
}
