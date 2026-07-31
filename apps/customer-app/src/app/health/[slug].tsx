import { useLocalSearchParams } from 'expo-router';
import React from 'react';

import MedicalReportsScreen from './reports';
import VaccinationsScreen from './vaccinations';

export default function HealthSlugScreen() {
  const { slug } = useLocalSearchParams<{ slug: string }>();

  if (slug === 'reports') {
    return <MedicalReportsScreen />;
  }

  return <VaccinationsScreen />;
}
