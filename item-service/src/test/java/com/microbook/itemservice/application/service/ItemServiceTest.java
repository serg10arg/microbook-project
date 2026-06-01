package com.microbook.itemservice.application.service;

import com.microbook.itemservice.domain.exception.ItemNotFoundException;
import com.microbook.itemservice.domain.model.Item;
import com.microbook.itemservice.domain.port.in.ItemUseCase.CreateItemCommand;
import com.microbook.itemservice.domain.port.in.ItemUseCase.UpdateItemCommand;
import com.microbook.itemservice.domain.port.out.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * Tests unitarios de ItemService.
 *
 * Decisiones de diseño:
 * ─────────────────────
 * @ExtendWith(MockitoExtension.class)
 *   Activa Mockito sin levantar contexto Spring.
 *   Cada test corre en ~5ms, no en los ~3s de @SpringBootTest.
 *
 * @Nested
 *   Agrupa los tests por método. El reporte de fallos muestra
 *   "ItemService > findById() > throws exception when not found"
 *   en lugar de una lista plana difícil de leer.
 *
 * BDDMockito (given/when/then)
 *   Mismo vocabulario que el patrón AAA (Arrange/Act/Assert)
 *   pero con nombres que lo hacen explícito en el código.
 *
 * AssertJ en lugar de JUnit assertions
 *   assertThat(result).isEqualTo(x)  es más legible que assertEquals(x, result)
 *   assertThatThrownBy(...)           captura y verifica excepciones en una línea
 *   El mensaje de fallo de AssertJ también es más informativo.
 *
 * ArgumentCaptor
 *   Captura el objeto exacto que se pasó al repositorio para verificar
 *   que el dominio construyó el Item correctamente antes de persistirlo.
 *   Más preciso que any(Item.class).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService")
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    // ── Fixture compartido ────────────────────────────────────────────────────
    // Se recrea antes de cada test con @BeforeEach para que cada test
    // parta de un estado limpio, sin efectos secundarios entre tests.

    private Item laptop;
    private Item monitor;

    @BeforeEach
    void setUp() {
        laptop  = Item.create("Laptop Dell XPS", "High performance laptop", 10);
        monitor = Item.create("Monitor 4K",      "UHD display 32 inch",     5);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findAll()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns all items from the repository")
        void returnsAllItems() {
            // Arrange
            given(itemRepository.findAll()).willReturn(List.of(laptop, monitor));

            // Act
            List<Item> result = itemService.findAll();

            // Assert
            assertThat(result)
                    .hasSize(2)
                    .containsExactly(laptop, monitor);

            // Verificar que se llamó al repositorio exactamente una vez
            then(itemRepository).should(times(1)).findAll();
        }

        @Test
        @DisplayName("returns empty list when no items exist")
        void returnsEmptyList_whenRepositoryIsEmpty() {
            given(itemRepository.findAll()).willReturn(List.of());

            List<Item> result = itemService.findAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("never calls save or delete when only reading")
        void doesNotMutate_whenFindAll() {
            given(itemRepository.findAll()).willReturn(List.of(laptop));

            itemService.findAll();

            // Verificar que no se hizo ninguna escritura inadvertida
            then(itemRepository).should(never()).save(any());
            then(itemRepository).should(never()).deleteById(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findById()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns the item when it exists")
        void returnsItem_whenFound() {
            given(itemRepository.findById(laptop.getId()))
                    .willReturn(Optional.of(laptop));

            Item result = itemService.findById(laptop.getId());

            assertThat(result).isEqualTo(laptop);
            assertThat(result.getName()).isEqualTo("Laptop Dell XPS");
        }

        @Test
        @DisplayName("throws ItemNotFoundException when item does not exist")
        void throwsItemNotFoundException_whenNotFound() {
            String missingId = "id-que-no-existe";
            given(itemRepository.findById(missingId)).willReturn(Optional.empty());

            // assertThatThrownBy: ejecuta el lambda y verifica la excepción lanzada
            assertThatThrownBy(() -> itemService.findById(missingId))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(missingId);
        }

        @Test
        @DisplayName("exception carries the missing item ID")
        void exceptionCarriesItemId_whenNotFound() {
            String missingId = "abc-123";
            given(itemRepository.findById(missingId)).willReturn(Optional.empty());

            // Capturar la excepción para inspeccionar sus propiedades
            ItemNotFoundException ex = catchThrowableOfType(
                    () -> itemService.findById(missingId),
                    ItemNotFoundException.class
            );

            assertThat(ex.getItemId()).isEqualTo(missingId);
        }

        @Test
        @DisplayName("never calls save when only reading by ID")
        void doesNotMutate_whenFindById() {
            given(itemRepository.findById(laptop.getId()))
                    .willReturn(Optional.of(laptop));

            itemService.findById(laptop.getId());

            then(itemRepository).should(never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // create()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates item with correct data and returns it")
        void createsItem_withCorrectData() {
            // Arrange
            var command = new CreateItemCommand("Teclado Mecánico", "Cherry MX Red", 20);

            // willAnswer devuelve el mismo objeto que recibió como argumento:
            // simula que el repositorio guarda y devuelve el item sin modificarlo
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Act
            Item result = itemService.create(command);

            // Assert — datos del item creado
            assertThat(result.getName()).isEqualTo("Teclado Mecánico");
            assertThat(result.getDescription()).isEqualTo("Cherry MX Red");
            assertThat(result.getQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("generated ID is not blank")
        void generatedId_isNotBlank() {
            var command = new CreateItemCommand("Mouse Logitech", "Wireless mouse", 15);
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Item result = itemService.create(command);

            // El dominio genera el UUID — verificar que no está vacío
            assertThat(result.getId()).isNotBlank();
        }

        @Test
        @DisplayName("createdAt and updatedAt are set on creation")
        void timestamps_areSet_onCreation() {
            var command = new CreateItemCommand("Hub USB", "7 ports", 8);
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Item result = itemService.create(command);

            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("the exact item passed to repository matches the command")
        void itemPassedToRepository_matchesCommand() {
            // ArgumentCaptor: captura el objeto exacto que se pasó a save()
            // para verificarlo en detalle, más allá de any(Item.class)
            var captor = ArgumentCaptor.forClass(Item.class);
            var command = new CreateItemCommand("Webcam HD", "1080p webcam", 3);

            given(itemRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            itemService.create(command);

            Item capturedItem = captor.getValue();
            assertThat(capturedItem.getName()).isEqualTo("Webcam HD");
            assertThat(capturedItem.getQuantity()).isEqualTo(3);
            assertThat(capturedItem.getId()).isNotBlank(); // ID generado por el dominio
        }

        @Test
        @DisplayName("repository.save is called exactly once")
        void callsSaveExactlyOnce() {
            var command = new CreateItemCommand("SSD 1TB", "NVMe SSD", 12);
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            itemService.create(command);

            then(itemRepository).should(times(1)).save(any(Item.class));
        }

        @ParameterizedTest(name = "quantity = {0}")
        @ValueSource(ints = {0, 1, 100, 9999})
        @DisplayName("accepts valid quantity values (0 or positive)")
        void acceptsValidQuantities(int quantity) {
            var command = new CreateItemCommand("Item X", "Some desc", quantity);
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> itemService.create(command))
                    .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // update()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("updates name, description and quantity of existing item")
        void updatesItem_whenFound() {
            // Arrange
            given(itemRepository.findById(laptop.getId()))
                    .willReturn(Optional.of(laptop));
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateItemCommand("Laptop Actualizada", "Nueva descripción", 25);

            // Act
            Item result = itemService.update(laptop.getId(), command);

            // Assert
            assertThat(result.getName()).isEqualTo("Laptop Actualizada");
            assertThat(result.getDescription()).isEqualTo("Nueva descripción");
            assertThat(result.getQuantity()).isEqualTo(25);
        }

        @Test
        @DisplayName("updatedAt is refreshed after update")
        void updatedAt_isRefreshed_afterUpdate() throws InterruptedException {
            given(itemRepository.findById(laptop.getId()))
                    .willReturn(Optional.of(laptop));
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            var originalUpdatedAt = laptop.getUpdatedAt();

            // Pequeña pausa para garantizar que el timestamp cambie
            Thread.sleep(5);

            var command = new UpdateItemCommand("Laptop v2", "Desc v2", 7);
            Item result = itemService.update(laptop.getId(), command);

            assertThat(result.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("throws ItemNotFoundException when item does not exist")
        void throwsItemNotFoundException_whenNotFound() {
            String missingId = "id-fantasma";
            given(itemRepository.findById(missingId)).willReturn(Optional.empty());

            var command = new UpdateItemCommand("X", "Y", 1);

            assertThatThrownBy(() -> itemService.update(missingId, command))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(missingId);
        }

        @Test
        @DisplayName("does not call save when item is not found")
        void doesNotSave_whenItemNotFound() {
            given(itemRepository.findById("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.update("ghost",
                    new UpdateItemCommand("X", "Y", 1)))
                    .isInstanceOf(ItemNotFoundException.class);

            // Si el item no existe, no debe intentarse ningún save
            then(itemRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("ID does not change after update")
        void id_doesNotChange_afterUpdate() {
            String originalId = laptop.getId();
            given(itemRepository.findById(originalId))
                    .willReturn(Optional.of(laptop));
            given(itemRepository.save(any(Item.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateItemCommand("Nuevo nombre", "Nueva desc", 5);
            Item result = itemService.update(originalId, command);

            assertThat(result.getId()).isEqualTo(originalId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // delete()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deletes existing item without throwing")
        void deletesItem_whenFound() {
            given(itemRepository.existsById(laptop.getId())).willReturn(true);
            willDoNothing().given(itemRepository).deleteById(laptop.getId());

            // assertThatCode: verifica que NO se lanza ninguna excepción
            assertThatCode(() -> itemService.delete(laptop.getId()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("calls deleteById exactly once with the correct ID")
        void callsDeleteById_withCorrectId() {
            given(itemRepository.existsById(laptop.getId())).willReturn(true);
            willDoNothing().given(itemRepository).deleteById(laptop.getId());

            itemService.delete(laptop.getId());

            // Verificar que deleteById se llamó con el ID correcto
            then(itemRepository).should(times(1)).deleteById(laptop.getId());
        }

        @Test
        @DisplayName("throws ItemNotFoundException when item does not exist")
        void throwsItemNotFoundException_whenNotFound() {
            String missingId = "no-existe";
            given(itemRepository.existsById(missingId)).willReturn(false);

            assertThatThrownBy(() -> itemService.delete(missingId))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(missingId);
        }

        @Test
        @DisplayName("never calls deleteById when item is not found")
        void doesNotCallDeleteById_whenNotFound() {
            given(itemRepository.existsById("ghost")).willReturn(false);

            assertThatThrownBy(() -> itemService.delete("ghost"))
                    .isInstanceOf(ItemNotFoundException.class);

            // Si no existe, no debe llamarse deleteById bajo ninguna circunstancia
            then(itemRepository).should(never()).deleteById(any());
        }

        @Test
        @DisplayName("calls existsById before attempting delete")
        void checksExistence_beforeDeletion() {
            given(itemRepository.existsById(laptop.getId())).willReturn(true);
            willDoNothing().given(itemRepository).deleteById(laptop.getId());

            itemService.delete(laptop.getId());

            // Verificar el orden: primero existsById, luego deleteById
            var inOrder = inOrder(itemRepository);
            inOrder.verify(itemRepository).existsById(laptop.getId());
            inOrder.verify(itemRepository).deleteById(laptop.getId());
        }
    }
}