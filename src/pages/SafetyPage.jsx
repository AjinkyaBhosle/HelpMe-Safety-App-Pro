import React from 'react';
import SafetyCamera from '../components/SafetyCamera';

const SafetyPage = ({ onBack }) => {
    return (
        <SafetyCamera onClose={onBack} />
    );
};

export default SafetyPage;
