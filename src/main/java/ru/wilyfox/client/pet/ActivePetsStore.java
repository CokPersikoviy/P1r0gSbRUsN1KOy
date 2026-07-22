package ru.wilyfox.client.pet;

import java.util.ArrayList;
import java.util.List;

public class ActivePetsStore {
    private List<ActivePetInfo> pets = new ArrayList<>();

    public void replace(List<ActivePetInfo> updatedPets) {
        this.pets = new ArrayList<>(updatedPets);
    }

    public List<ActivePetInfo> getAll() {
        return List.copyOf(pets);
    }

    public List<ActivePetInfo> getResolved() {
        return pets.stream().filter(ActivePetInfo::resolved).toList();
    }

    public boolean hasResolved() {
        return pets.stream().anyMatch(ActivePetInfo::resolved);
    }

    public boolean isEmpty() {
        return pets.isEmpty();
    }

    public void clear() {
        pets.clear();
    }
}
