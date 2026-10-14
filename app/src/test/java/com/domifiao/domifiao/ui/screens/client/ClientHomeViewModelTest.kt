package com.domifiao.domifiao.ui.screens.client

import com.domifiao.domifiao.data.repository.ColmadoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ClientHomeViewModelTest {

    @Mock
    private lateinit var mockAuth: FirebaseAuth

    @Mock
    private lateinit var mockUser: FirebaseUser

    @Mock
    private lateinit var mockDb: FirebaseFirestore

    private lateinit var viewModel: ClientHomeViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockAuth.currentUser).thenReturn(mockUser)
        `when`(mockUser.uid).thenReturn("test_user_id")
        
        // Inicializamos con Mocks para evitar la dependencia de Android real
        viewModel = ClientHomeViewModel(mockAuth, mockDb)
    }

    @Test
    fun `initial state is loading`() {
        val initialState = viewModel.uiState.value
        assertEquals(true, initialState.isLoading)
        assertEquals(false, initialState.isGpsLoading)
        assertEquals("", initialState.searchText)
    }

    @Test
    fun `search text updates correctly`() {
        viewModel.onSearchTextChanged("Colmado La Fe")
        assertEquals("Colmado La Fe", viewModel.uiState.value.searchText)
    }

    @Test
    fun `toggle points filter updates correctly`() {
        val initial = viewModel.uiState.value.showOnlyWithPoints
        viewModel.toggleShowOnlyWithPoints()
        assertEquals(!initial, viewModel.uiState.value.showOnlyWithPoints)
    }
}