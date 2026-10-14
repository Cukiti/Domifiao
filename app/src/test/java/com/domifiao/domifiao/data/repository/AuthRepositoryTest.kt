package com.domifiao.domifiao.data.repository

import com.domifiao.domifiao.data.util.Resource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class AuthRepositoryTest {

    @Mock
    private lateinit var auth: FirebaseAuth

    @Mock
    private lateinit var db: FirebaseFirestore

    @Mock
    private lateinit var firebaseUser: FirebaseUser

    @Mock
    private lateinit var collectionRef: CollectionReference

    @Mock
    private lateinit var documentRef: DocumentReference

    private lateinit var repository: AuthRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = AuthRepository(auth, db)
    }

    @Test
    fun `updateOnlineStatus returns Error when user is not authenticated`() = runBlocking {
        // GIVEN
        `when`(auth.currentUser).thenReturn(null)

        // WHEN
        val result = repository.updateOnlineStatus(true)

        // THEN
        assertTrue(result is Resource.Error)
        assertEquals("Usuario no autenticado", (result as Resource.Error).message)
    }

    @Test
    fun `updateOnlineStatus returns Success when update on first collection succeeds`() = runBlocking {
        // GIVEN
        val userId = "test_user_id"
        `when`(auth.currentUser).thenReturn(firebaseUser)
        `when`(firebaseUser.uid).thenReturn(userId)
        `when`(db.collection("users")).thenReturn(collectionRef)
        `when`(collectionRef.document(userId)).thenReturn(documentRef)
        `when`(documentRef.update(org.mockito.ArgumentMatchers.anyMap<String, Any>())).thenReturn(Tasks.forResult(null))

        // WHEN
        val result = repository.updateOnlineStatus(true)

        // THEN
        assertTrue(result is Resource.Success)
    }

    @Test
    fun `updateOnlineStatus uses fallback collection when first collection update fails`() = runBlocking {
        // GIVEN
        val userId = "test_user_id"
        `when`(auth.currentUser).thenReturn(firebaseUser)
        `when`(firebaseUser.uid).thenReturn(userId)
        
        // Mock failure for "users"
        `when`(db.collection("users")).thenReturn(collectionRef)
        `when`(collectionRef.document(userId)).thenReturn(documentRef)
        `when`(documentRef.update(org.mockito.ArgumentMatchers.anyMap<String, Any>())).thenReturn(Tasks.forException(Exception("Fallo colección 1")))
        
        // Mock success for "colmados_requests" (fallback)
        val fallbackCollectionRef = org.mockito.Mockito.mock(CollectionReference::class.java)
        val fallbackDocumentRef = org.mockito.Mockito.mock(DocumentReference::class.java)
        `when`(db.collection("colmados_requests")).thenReturn(fallbackCollectionRef)
        `when`(fallbackCollectionRef.document(userId)).thenReturn(fallbackDocumentRef)
        `when`(fallbackDocumentRef.update(org.mockito.ArgumentMatchers.anyMap<String, Any>())).thenReturn(Tasks.forResult(null))

        // WHEN
        val result = repository.updateOnlineStatus(true)

        // THEN
        assertTrue(result is Resource.Success)
    }
}